package com.bekololek.pluginfactory.container;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContainerSessionRepository extends JpaRepository<ContainerSession, UUID> {

    List<ContainerSession> findByIterationId(UUID iterationId);

    List<ContainerSession> findByReleasedAtIsNull();

    void deleteByIterationIdIn(List<UUID> iterationIds);
}
