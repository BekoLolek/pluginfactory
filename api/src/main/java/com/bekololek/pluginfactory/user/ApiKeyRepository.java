package com.bekololek.pluginfactory.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByUserIdAndRevokedFalse(UUID userId);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    List<ApiKey> findByUserId(UUID userId);
}
