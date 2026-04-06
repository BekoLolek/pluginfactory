package com.bekololek.pluginfactory.container;

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
@Table(name = "container_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ContainerSession {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "iteration_id", nullable = false)
    private UUID iterationId;

    @Column(name = "container_id", nullable = false)
    private String containerId;

    @Column(name = "container_type", nullable = false)
    private String containerType;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "memory_mb", nullable = false)
    private int memoryMb;

    @Column(name = "cpu_millicores", nullable = false)
    private int cpuMillicores;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (claimedAt == null) {
            claimedAt = Instant.now();
        }
    }
}
