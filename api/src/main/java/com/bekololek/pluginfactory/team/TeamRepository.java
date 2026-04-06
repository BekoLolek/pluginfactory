package com.bekololek.pluginfactory.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByOwner_Id(UUID ownerId);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.user.id = :userId")
    List<Team> findTeamsByMemberId(@Param("userId") UUID userId);
}
