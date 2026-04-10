package com.bekololek.pluginfactory.build;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TokenBudgetRepository extends JpaRepository<TokenBudget, UUID> {

    Optional<TokenBudget> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
