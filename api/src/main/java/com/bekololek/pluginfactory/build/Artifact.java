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
@Table(name = "artifacts")
@Getter
@Setter
@NoArgsConstructor
public class Artifact {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "iteration_id", nullable = false)
    private UUID iterationId;

    @Column(name = "jar_file_path")
    private String jarFilePath;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "plugin_version")
    private String pluginVersion;

    @Column(name = "plugin_yml", columnDefinition = "TEXT")
    private String pluginYml;

    @Column(name = "security_passed", nullable = false)
    private boolean securityPassed;

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
