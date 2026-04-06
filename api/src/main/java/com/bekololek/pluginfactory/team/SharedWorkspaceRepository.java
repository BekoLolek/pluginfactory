package com.bekololek.pluginfactory.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SharedWorkspaceRepository extends JpaRepository<SharedWorkspace, UUID> {

    List<SharedWorkspace> findByTeam_Id(UUID teamId);
}
