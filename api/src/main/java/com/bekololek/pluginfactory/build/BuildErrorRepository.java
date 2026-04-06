package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BuildErrorRepository extends JpaRepository<BuildError, UUID> {

    List<BuildError> findByIterationId(UUID iterationId);
}
