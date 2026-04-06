package com.bekololek.pluginfactory.build;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import com.bekololek.pluginfactory.user.User;

@Entity
@Table(name = "source_code_requests")
@Getter
@Setter
@NoArgsConstructor
public class SourceCodeRequest {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "artifact_id", nullable = false)
    private Artifact artifact;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "license_version", nullable = false)
    private String licenseVersion;

    @Column(name = "license_accepted_at")
    private Instant licenseAcceptedAt;

    @Column(name = "license_accepted_ip")
    private String licenseAcceptedIp;

    @Column(name = "watermark_id", nullable = false)
    private UUID watermarkId;

    @Column(name = "download_path")
    private String downloadPath;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
