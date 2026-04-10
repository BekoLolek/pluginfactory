package com.bekololek.pluginfactory.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanDocumentRepository extends JpaRepository<PlanDocument, UUID> {

    Optional<PlanDocument> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
