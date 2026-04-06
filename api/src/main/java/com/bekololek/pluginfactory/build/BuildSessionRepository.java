package com.bekololek.pluginfactory.build;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuildSessionRepository extends JpaRepository<BuildSession, UUID> {

    Optional<BuildSession> findByIdAndUserId(UUID id, UUID userId);

    Page<BuildSession> findByUserId(UUID userId, Pageable pageable);

    Page<BuildSession> findByUserIdAndStatus(UUID userId, BuildStatus status, Pageable pageable);

    long countByUserIdAndStatusIn(UUID userId, List<BuildStatus> statuses);

    List<BuildSession> findByWorkspaceId(UUID workspaceId);
}
