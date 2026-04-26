package com.bekololek.pluginfactory.build;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BuildErrorRepository extends JpaRepository<BuildError, UUID> {

    List<BuildError> findByIterationId(UUID iterationId);

    List<BuildError> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    void deleteByIterationIdIn(List<UUID> iterationIds);

    void deleteBySessionId(UUID sessionId);

    @Query("SELECT e FROM BuildError e WHERE " +
            "(CAST(:from AS timestamp) IS NULL OR e.createdAt >= :from) AND " +
            "(CAST(:to AS timestamp) IS NULL OR e.createdAt <= :to)")
    List<BuildError> findWithFilters(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT e FROM BuildError e " +
            "JOIN BuildSession s ON s.id = e.sessionId " +
            "WHERE (:sessionId IS NULL OR e.sessionId = :sessionId) " +
            "AND (:userId IS NULL OR s.userId = :userId) " +
            "AND (:category IS NULL OR e.category = :category) " +
            "AND (:severity IS NULL OR e.severity = :severity) " +
            "AND (CAST(:from AS timestamp) IS NULL OR e.createdAt >= :from) " +
            "AND (CAST(:to AS timestamp) IS NULL OR e.createdAt <= :to)")
    Page<BuildError> findRecentWithFilters(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("category") String category,
            @Param("severity") String severity,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    BuildError findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
