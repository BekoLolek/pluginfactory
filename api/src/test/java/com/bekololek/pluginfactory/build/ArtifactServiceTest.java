package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private SourceBundleRepository sourceBundleRepository;

    @Mock
    private BuildSessionService buildSessionService;

    @TempDir
    Path tempDir;

    private ArtifactService artifactService;

    @BeforeEach
    void setUp() {
        artifactService = new ArtifactService(
                artifactRepository,
                sourceBundleRepository,
                buildSessionService,
                tempDir.toString()
        );
    }

    @Test
    void storeArtifact_createsFilesAndRecords() {
        UUID sessionId = UUID.randomUUID();
        UUID iterationId = UUID.randomUUID();
        byte[] jarBytes = "fake-jar-content".getBytes();
        byte[] sourceZipBytes = "fake-source-content".getBytes();
        String pluginYml = "name: Test";

        when(artifactRepository.save(any(Artifact.class))).thenAnswer(invocation -> {
            Artifact a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(sourceBundleRepository.save(any(SourceBundle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Artifact result = artifactService.storeArtifact(sessionId, iterationId, jarBytes, sourceZipBytes,
                pluginYml, true);

        // Verify artifact record
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo(sessionId);
        assertThat(result.getIterationId()).isEqualTo(iterationId);
        assertThat(result.getPluginYml()).isEqualTo(pluginYml);
        assertThat(result.isSecurityPassed()).isTrue();
        assertThat(result.getFileHash()).isNotNull().isNotBlank();
        assertThat(result.getFileSizeBytes()).isEqualTo(jarBytes.length);

        // Verify files exist on disk
        Path jarPath = tempDir.resolve(sessionId.toString()).resolve(iterationId.toString()).resolve("plugin.jar");
        Path sourceZipPath = tempDir.resolve(sessionId.toString()).resolve(iterationId.toString()).resolve("source.zip");
        assertThat(Files.exists(jarPath)).isTrue();
        assertThat(Files.exists(sourceZipPath)).isTrue();
    }

    @Test
    void downloadArtifact_success() throws IOException {
        UUID artifactId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] jarContent = "test-jar".getBytes();

        // Create the file on disk
        Path jarDir = tempDir.resolve("jar");
        Files.createDirectories(jarDir);
        Path jarPath = jarDir.resolve("plugin.jar");
        Files.write(jarPath, jarContent);

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);
        artifact.setJarFilePath(jarPath.toString());

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));

        BuildSession session = new BuildSession();
        session.setUserId(userId);
        when(buildSessionService.getSessionById(sessionId)).thenReturn(session);

        byte[] result = artifactService.downloadArtifact(artifactId, userId);

        assertThat(result).isEqualTo(jarContent);
    }

    @Test
    void downloadArtifact_wrongUser_forbidden() {
        UUID artifactId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);
        artifact.setJarFilePath("some/path");

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));

        BuildSession session = new BuildSession();
        session.setUserId(otherUserId);
        when(buildSessionService.getSessionById(sessionId)).thenReturn(session);

        assertThatThrownBy(() -> artifactService.downloadArtifact(artifactId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied to artifact");
    }

    @Test
    void downloadArtifact_notFound() {
        UUID artifactId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artifactService.downloadArtifact(artifactId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Artifact not found");
    }

    @Test
    void listArtifacts_returnsAll() {
        UUID sessionId = UUID.randomUUID();
        Artifact a1 = new Artifact();
        a1.setSessionId(sessionId);
        Artifact a2 = new Artifact();
        a2.setSessionId(sessionId);

        when(artifactRepository.findBySessionId(sessionId)).thenReturn(List.of(a1, a2));

        List<Artifact> result = artifactService.listArtifacts(sessionId);

        assertThat(result).hasSize(2);
    }

    @Test
    void computeSha256_consistency() {
        byte[] data = "test data".getBytes();

        String hash1 = artifactService.computeSha256(data);
        String hash2 = artifactService.computeSha256(data);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex is 64 chars
    }

    @Test
    void computeSha256_differentData_differentHash() {
        String hash1 = artifactService.computeSha256("data1".getBytes());
        String hash2 = artifactService.computeSha256("data2".getBytes());

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void downloadSource_success() throws IOException {
        UUID artifactId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] sourceContent = "test-source-zip".getBytes();

        // Create the file on disk
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path sourcePath = sourceDir.resolve("source.zip");
        Files.write(sourcePath, sourceContent);

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(sessionId);

        SourceBundle sourceBundle = new SourceBundle();
        sourceBundle.setArtifactId(artifactId);
        sourceBundle.setSourceZipPath(sourcePath.toString());

        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));
        when(sourceBundleRepository.findByArtifactId(artifactId)).thenReturn(Optional.of(sourceBundle));

        BuildSession session = new BuildSession();
        session.setUserId(userId);
        when(buildSessionService.getSessionById(sessionId)).thenReturn(session);

        byte[] result = artifactService.downloadSource(artifactId, userId);

        assertThat(result).isEqualTo(sourceContent);
    }
}
