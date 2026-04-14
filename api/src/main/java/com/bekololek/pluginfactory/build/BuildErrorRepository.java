package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BuildErrorRepository extends JpaRepository<BuildError, UUID> {

    List<BuildError> findByIterationId(UUID iterationId);

    void deleteByIterationIdIn(List<UUID> iterationIds);

    @Query("SELECT e FROM BuildError e WHERE " +
            "(CAST(:from AS timestamp) IS NULL OR e.createdAt >= :from) AND " +
            "(CAST(:to AS timestamp) IS NULL OR e.createdAt <= :to)")
    List<BuildError> findWithFilters(
            @Param("from") Instant from,
            @Param("to") Instant to);
}
