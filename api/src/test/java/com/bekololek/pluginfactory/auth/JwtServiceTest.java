package com.bekololek.pluginfactory.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 900000, 604800000);
    }

    @Test
    void generateAndValidateAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "USER");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();

        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getId()).isNotNull();
    }

    @Test
    void generateAndValidateRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateRefreshToken(userId);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();

        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getId()).isNotNull();
    }

    @Test
    void extractUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "USER");

        UUID extractedId = jwtService.extractUserId(token);
        assertThat(extractedId).isEqualTo(userId);
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService shortLivedService = new JwtService(SECRET, 1, 1);
        UUID userId = UUID.randomUUID();
        String token = shortLivedService.generateAccessToken(userId, "USER");

        // Wait for expiration
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    void tamperedTokenIsInvalid() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "USER");
        String tamperedToken = token + "tampered";

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyIsInvalid() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "USER");

        JwtService otherService = new JwtService(
                "different-secret-key-at-least-256-bits-long-for-hs256-algorithm-other", 900000, 604800000);

        assertThat(otherService.isTokenValid(token)).isFalse();
    }

    @Test
    void eachTokenHasUniqueJti() {
        UUID userId = UUID.randomUUID();
        String token1 = jwtService.generateAccessToken(userId, "USER");
        String token2 = jwtService.generateAccessToken(userId, "USER");

        Claims claims1 = jwtService.validateToken(token1);
        Claims claims2 = jwtService.validateToken(token2);

        assertThat(claims1.getId()).isNotEqualTo(claims2.getId());
    }
}
