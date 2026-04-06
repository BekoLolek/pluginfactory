package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    List<Artifact> findBySessionId(UUID sessionId);

    List<Artifact> findByIterationId(UUID iterationId);
}
