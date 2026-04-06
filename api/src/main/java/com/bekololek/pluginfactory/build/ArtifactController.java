package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.ArtifactDto;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;
    private final BuildSessionService buildSessionService;

    @GetMapping("/builds/{sessionId}/artifacts")
    public ResponseEntity<List<ArtifactDto>> listArtifacts(@PathVariable UUID sessionId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        // Ownership check
        buildSessionService.getSession(sessionId, userId);

        List<ArtifactDto> artifacts = artifactService.listArtifacts(sessionId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(artifacts);
    }

    @GetMapping("/artifacts/{artifactId}/download")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable UUID artifactId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        byte[] jarBytes = artifactService.downloadArtifact(artifactId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plugin.jar\"")
                .contentType(MediaType.parseMediaType("application/java-archive"))
                .body(jarBytes);
    }

    @GetMapping("/artifacts/{artifactId}/source")
    public ResponseEntity<byte[]> downloadSource(@PathVariable UUID artifactId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        byte[] sourceBytes = artifactService.downloadSource(artifactId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"source.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(sourceBytes);
    }

    private ArtifactDto toDto(Artifact artifact) {
        return new ArtifactDto(
                artifact.getId(),
                artifact.getSessionId(),
                artifact.getIterationId(),
                artifact.getFileHash(),
                artifact.getFileSizeBytes(),
                artifact.getPluginVersion(),
                artifact.isSecurityPassed(),
                artifact.getCreatedAt()
        );
    }
}
