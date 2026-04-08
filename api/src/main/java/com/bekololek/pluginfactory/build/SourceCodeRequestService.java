package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.SourceCodeRequestDto;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class SourceCodeRequestService {

    private final SourceCodeRequestRepository sourceCodeRequestRepository;
    private final ArtifactRepository artifactRepository;
    private final SourceBundleRepository sourceBundleRepository;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final WatermarkService watermarkService;
    private final BuildSessionService buildSessionService;
    private final Path storagePath;

    public SourceCodeRequestService(SourceCodeRequestRepository sourceCodeRequestRepository,
                                     ArtifactRepository artifactRepository,
                                     SourceBundleRepository sourceBundleRepository,
                                     SubscriptionService subscriptionService,
                                     UserRepository userRepository,
                                     WatermarkService watermarkService,
                                     BuildSessionService buildSessionService,
                                     @Value("${artifacts.storage-path:data/artifacts}") String storagePath) {
        this.sourceCodeRequestRepository = sourceCodeRequestRepository;
        this.artifactRepository = artifactRepository;
        this.sourceBundleRepository = sourceBundleRepository;
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
        this.watermarkService = watermarkService;
        this.buildSessionService = buildSessionService;
        this.storagePath = Path.of(storagePath);
    }

    @Transactional
    public SourceCodeRequestDto requestSourceCode(UUID userId, UUID artifactId,
                                                    String licenseVersion, String clientIp) {
        // Validate tier - only PRO and TEAM have source code access
        Tier tier = subscriptionService.getTierForUser(userId);
        if (!tier.isSourceCodeAccess()) {
            throw new ForbiddenException("Source code access requires PRO or TEAM subscription");
        }

        // Validate artifact exists
        Artifact artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new NotFoundException("Artifact not found"));

        // Validate artifact ownership — the requester must own the session that
        // produced this artifact. Without this check any PRO/TEAM user could
        // request (and subsequently fulfill) source code for another user's build.
        BuildSession session = buildSessionService.getSessionById(artifact.getSessionId());
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("Access denied to artifact");
        }

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Create request
        SourceCodeRequest request = new SourceCodeRequest();
        request.setUser(user);
        request.setArtifact(artifact);
        request.setStatus("PENDING");
        request.setLicenseVersion(licenseVersion);
        request.setLicenseAcceptedAt(Instant.now());
        request.setLicenseAcceptedIp(clientIp);
        request.setWatermarkId(UUID.randomUUID());

        request = sourceCodeRequestRepository.save(request);

        log.info("Source code request {} created for user {} and artifact {}",
                request.getId(), userId, artifactId);

        return toDto(request);
    }

    @Transactional
    public SourceCodeRequestDto fulfillRequest(UUID requestId, UUID userId) {
        SourceCodeRequest request = sourceCodeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Source code request not found"));

        if (!request.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Access denied to source code request");
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new ValidationException("Request is not in PENDING status");
        }

        // Get the source bundle for the artifact
        SourceBundle sourceBundle = sourceBundleRepository.findByArtifactId(request.getArtifact().getId())
                .orElseThrow(() -> new NotFoundException("Source bundle not found for artifact"));

        try {
            // Read source ZIP and extract files
            Map<String, String> sourceFiles = extractSourceFiles(Path.of(sourceBundle.getSourceZipPath()));

            // Apply watermark
            Map<String, String> watermarkedFiles = watermarkService.watermarkSource(
                    sourceFiles,
                    request.getArtifact().getSessionId(),
                    request.getUser().getId(),
                    request.getWatermarkId()
            );

            // Create watermarked ZIP
            byte[] watermarkedZip = createZip(watermarkedFiles);

            // Store watermarked ZIP
            Path downloadDir = storagePath.resolve("watermarked").resolve(requestId.toString());
            Files.createDirectories(downloadDir);
            Path downloadPath = downloadDir.resolve("source-watermarked.zip");
            Files.write(downloadPath, watermarkedZip);

            // Update request
            request.setDownloadPath(downloadPath.toString());
            request.setStatus("FULFILLED");
            request.setFulfilledAt(Instant.now());
            request = sourceCodeRequestRepository.save(request);

            log.info("Source code request {} fulfilled, watermarked ZIP stored at {}",
                    requestId, downloadPath);

            return toDto(request);

        } catch (IOException e) {
            throw new RuntimeException("Failed to fulfill source code request: " + e.getMessage(), e);
        }
    }

    @Transactional
    public byte[] downloadSourceCode(UUID requestId, UUID userId) {
        SourceCodeRequest request = sourceCodeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Source code request not found"));

        // Validate ownership
        if (!request.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Access denied to source code request");
        }

        // Validate status
        if (!"FULFILLED".equals(request.getStatus()) && !"DOWNLOADED".equals(request.getStatus())) {
            throw new ValidationException("Source code request is not yet fulfilled");
        }

        try {
            byte[] zipBytes = Files.readAllBytes(Path.of(request.getDownloadPath()));

            // Update status to DOWNLOADED
            request.setStatus("DOWNLOADED");
            sourceCodeRequestRepository.save(request);

            log.info("Source code request {} downloaded by user {}", requestId, userId);

            return zipBytes;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read watermarked source file: " + e.getMessage(), e);
        }
    }

    Map<String, String> extractSourceFiles(Path zipPath) throws IOException {
        Map<String, String> files = new HashMap<>();

        try (InputStream fis = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                    throw new IOException("Invalid zip entry: " + entryName);
                }
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    files.put(entry.getName(), baos.toString());
                }
                zis.closeEntry();
            }
        }

        return files;
    }

    byte[] createZip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue().getBytes());
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

    private SourceCodeRequestDto toDto(SourceCodeRequest request) {
        return new SourceCodeRequestDto(
                request.getId(),
                request.getUser().getId(),
                request.getArtifact().getId(),
                request.getStatus(),
                request.getLicenseVersion(),
                request.getLicenseAcceptedAt(),
                request.getWatermarkId(),
                request.getRequestedAt(),
                request.getFulfilledAt()
        );
    }
}
