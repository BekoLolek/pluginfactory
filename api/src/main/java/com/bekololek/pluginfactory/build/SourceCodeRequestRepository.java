package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceCodeRequestRepository extends JpaRepository<SourceCodeRequest, UUID> {

    List<SourceCodeRequest> findByUser_Id(UUID userId);

    List<SourceCodeRequest> findByArtifact_Id(UUID artifactId);
}
