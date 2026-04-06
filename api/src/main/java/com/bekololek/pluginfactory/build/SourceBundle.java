package com.bekololek.pluginfactory.build;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_bundles")
@Getter
@Setter
@NoArgsConstructor
public class SourceBundle {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "artifact_id", nullable = false, unique = true)
    private UUID artifactId;

    @Column(name = "source_zip_path")
    private String sourceZipPath;

    @Column(name = "source_hash")
    private String sourceHash;

    @Column(name = "source_size_bytes")
    private Long sourceSizeBytes;

    @Column(name = "template_version")
    private String templateVersion;

    @Column(name = "build_tool", nullable = false)
    private String buildTool = "MAVEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "retention_expires_at")
    private Instant retentionExpiresAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
