package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceBundleRepository extends JpaRepository<SourceBundle, UUID> {

    Optional<SourceBundle> findByArtifactId(UUID artifactId);
}
