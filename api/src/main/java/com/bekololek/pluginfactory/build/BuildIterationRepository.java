package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BuildIterationRepository extends JpaRepository<BuildIteration, UUID> {

    List<BuildIteration> findBySessionIdOrderByIterationNumberAsc(UUID sessionId);
}
