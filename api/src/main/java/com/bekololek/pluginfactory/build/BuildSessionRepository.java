package com.bekololek.pluginfactory.build;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuildSessionRepository extends JpaRepository<BuildSession, UUID> {

    Optional<BuildSession> findByIdAndUserId(UUID id, UUID userId);

    Page<BuildSession> findByUserId(UUID userId, Pageable pageable);

    Page<BuildSession> findByUserIdAndStatus(UUID userId, BuildStatus status, Pageable pageable);

    long countByUserIdAndStatusIn(UUID userId, List<BuildStatus> statuses);

    long countByUserIdAndStatusInAndCreatedAtAfter(UUID userId, List<BuildStatus> statuses, Instant createdAtAfter);

    List<BuildSession> findByWorkspaceId(UUID workspaceId);

    List<BuildSession> findByStatusIn(List<BuildStatus> statuses);

    List<BuildSession> findByStatusInAndUpdatedAtBefore(List<BuildStatus> statuses, Instant cutoff);
}
