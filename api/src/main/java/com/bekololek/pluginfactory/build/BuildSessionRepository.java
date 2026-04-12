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

    List<BuildSession> findByStatusIn(List<BuildStatus> statuses);

    List<BuildSession> findByStatusInAndUpdatedAtBefore(List<BuildStatus> statuses, Instant cutoff);

    long countByCreatedAtAfter(Instant since);

    long countByStatus(BuildStatus status);

    long countByStatusAndCreatedAtBetween(BuildStatus status, Instant from, Instant to);

    long countByCreatedAtBetween(Instant from, Instant to);

    @Query("SELECT b FROM BuildSession b WHERE " +
            "(:status IS NULL OR b.status = :status) AND " +
            "(:userId IS NULL OR b.userId = :userId) AND " +
            "(:from IS NULL OR b.createdAt >= :from) AND " +
            "(:to IS NULL OR b.createdAt <= :to)")
    Page<BuildSession> findWithFilters(
            @Param("status") BuildStatus status,
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    List<BuildSession> findByCreatedAtBetween(Instant from, Instant to);
}
