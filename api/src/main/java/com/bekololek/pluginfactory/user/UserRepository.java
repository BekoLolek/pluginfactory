package com.bekololek.pluginfactory.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByDiscordId(String discordId);

    long countByLastActiveAtAfter(Instant since);

    long countByCreatedAtAfter(Instant since);

    @Query("SELECT u FROM User u WHERE " +
            "(CAST(:status AS text) IS NULL OR u.status = :status) AND " +
            "(CAST(:search AS text) IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) " +
            "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))")
    Page<User> findWithFilters(
            @Param("status") User.UserStatus status,
            @Param("search") String search,
            Pageable pageable);
}
