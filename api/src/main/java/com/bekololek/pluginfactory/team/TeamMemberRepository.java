package com.bekololek.pluginfactory.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeam_IdAndUser_Id(UUID teamId, UUID userId);

    List<TeamMember> findByTeam_Id(UUID teamId);

    boolean existsByTeam_IdAndUser_Id(UUID teamId, UUID userId);
}
