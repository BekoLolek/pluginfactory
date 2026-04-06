package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final SourceBundleRepository sourceBundleRepository;
    private final BuildSessionService buildSessionService;
    private final Path storagePath;

    public ArtifactService(ArtifactRepository artifactRepository,
                           SourceBundleRepository sourceBundleRepository,
                           BuildSessionService buildSessionService,
                           @Value("${artifacts.storage-path:data/artifacts}") String storagePath) {
        this.artifactRepository = artifactRepository;
        this.sourceBundleRepository = sourceBundleRepository;
        this.buildSessionService = buildSessionService;
        this.storagePath = Path.of(storagePath);
    }

    @Transactional
    public Artifact storeArtifact(UUID sessionId, UUID iterationId, byte[] jarBytes,
                                   byte[] sourceZipBytes, String pluginYml, boolean securityPassed) {
        try {
            // Create storage directory
            Path artifactDir = storagePath.resolve(sessionId.toString()).resolve(iterationId.toString());
            Files.createDirectories(artifactDir);

            // Store JAR
            Path jarPath = artifactDir.resolve("plugin.jar");
            Files.write(jarPath, jarBytes);

            // Store source ZIP
            Path sourceZipPath = artifactDir.resolve("source.zip");
            Files.write(sourceZipPath, sourceZipBytes);

            // Compute hashes
            String jarHash = computeSha256(jarBytes);
            String sourceHash = computeSha256(sourceZipBytes);

            // Create Artifact record
            Artifact artifact = new Artifact();
            artifact.setSessionId(sessionId);
            artifact.setIterationId(iterationId);
            artifact.setJarFilePath(jarPath.toString());
            artifact.setFileHash(jarHash);
            artifact.setFileSizeBytes((long) jarBytes.length);
            artifact.setPluginVersion("1.0.0");
            artifact.setPluginYml(pluginYml);
            artifact.setSecurityPassed(securityPassed);
            artifact.setRetentionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            artifact = artifactRepository.save(artifact);

            // Create SourceBundle record
            SourceBundle sourceBundle = new SourceBundle();
            sourceBundle.setArtifactId(artifact.getId());
            sourceBundle.setSourceZipPath(sourceZipPath.toString());
            sourceBundle.setSourceHash(sourceHash);
            sourceBundle.setSourceSizeBytes((long) sourceZipBytes.length);
            sourceBundle.setTemplateVersion("1.0.0");
            sourceBundle.setBuildTool("MAVEN");
            sourceBundle.setRetentionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            sourceBundleRepository.save(sourceBundle);

            log.info("Stored artifact {} for session {}, iteration {}",
                    artifact.getId(), sessionId, iterationId);
            return artifact;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store artifact: " + e.getMessage(), e);
        }
    }

    public byte[] downloadArtifact(UUID artifactId, UUID userId) {
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new NotFoundException("Artifact not found"));

        // Verify ownership
        BuildSession session = buildSessionService.getSessionById(artifact.getSessionId());
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to artifact");
        }

        try {
            return Files.readAllBytes(Path.of(artifact.getJarFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read artifact file: " + e.getMessage(), e);
        }
    }

    public byte[] downloadSource(UUID artifactId, UUID userId) {
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new NotFoundException("Artifact not found"));

        // Verify ownership
        BuildSession session = buildSessionService.getSessionById(artifact.getSessionId());
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to artifact");
        }

        SourceBundle sourceBundle = sourceBundleRepository.findByArtifactId(artifactId)
                .orElseThrow(() -> new NotFoundException("Source bundle not found"));

        try {
            return Files.readAllBytes(Path.of(sourceBundle.getSourceZipPath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read source file: " + e.getMessage(), e);
        }
    }

    public List<Artifact> listArtifacts(UUID sessionId) {
        return artifactRepository.findBySessionId(sessionId);
    }

    String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
