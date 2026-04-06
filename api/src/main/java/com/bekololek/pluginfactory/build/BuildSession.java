package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "build_sessions")
@Getter
@Setter
@NoArgsConstructor
public class BuildSession extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildStatus status = BuildStatus.CHATTING;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false)
    private BuildPhase currentPhase = BuildPhase.IDLE;

    @Column(name = "complexity_score")
    private Integer complexityScore;

    @Column(name = "completed_at")
    private Instant completedAt;
}
