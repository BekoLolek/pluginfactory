package com.bekololek.pluginfactory.build;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<BuildSession> findByStatusInAndCurrentPhaseNot(List<BuildStatus> statuses, BuildPhase excludedPhase);

    List<BuildSession> findByStatusInAndCurrentPhaseNotAndUpdatedAtBefore(
            List<BuildStatus> statuses, BuildPhase excludedPhase, Instant cutoff);

    long countByCreatedAtAfter(Instant since);

    long countByStatus(BuildStatus status);

    long countByStatusAndCreatedAtBetween(BuildStatus status, Instant from, Instant to);

    long countByCreatedAtBetween(Instant from, Instant to);

    @Query("SELECT b FROM BuildSession b WHERE " +
            "(CAST(:status AS text) IS NULL OR b.status = :status) AND " +
            "(CAST(:userId AS text) IS NULL OR b.userId = :userId) AND " +
            "(CAST(:from AS timestamp) IS NULL OR b.createdAt >= :from) AND " +
            "(CAST(:to AS timestamp) IS NULL OR b.createdAt <= :to)")
    Page<BuildSession> findWithFilters(
            @Param("status") BuildStatus status,
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    List<BuildSession> findByCreatedAtBetween(Instant from, Instant to);
}
