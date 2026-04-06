package com.bekololek.pluginfactory.auth;

import com.bekololek.pluginfactory.auth.dto.AuthResponse;
import com.bekololek.pluginfactory.auth.dto.DiscordTokenResponse;
import com.bekololek.pluginfactory.auth.dto.DiscordUserInfo;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionRepository;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final DiscordOAuthService discordOAuthService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public String getDiscordAuthorizationUrl() {
        return discordOAuthService.buildAuthorizationUrl();
    }

    @Transactional
    public AuthResponse handleDiscordCallback(String code, String state) {
        discordOAuthService.validateState(state);
        DiscordTokenResponse tokenResponse = discordOAuthService.exchangeCode(code);
        DiscordUserInfo discordUser = discordOAuthService.fetchDiscordUser(tokenResponse.accessToken());

        Optional<User> existingUser = userRepository.findByDiscordId(discordUser.id());
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            user = new User();
            user.setDiscordId(discordUser.id());
            user.setEmail(discordUser.email());
            user.setDisplayName(discordUser.username());
            user.setAuthProvider("discord");
            user = userRepository.save(user);

            Subscription subscription = new Subscription();
            subscription.setUserId(user.getId());
            subscription.setTier(Tier.FREE);
            subscriptionRepository.save(subscription);
        }

        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        storeRefreshToken(user.getId(), refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getDiscordId()
                )
        );
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        if (stored.isRevoked()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        UUID userId = stored.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        String newAccessToken = jwtService.generateAccessToken(userId, user.getRole().name());

        return new AuthResponse(
                newAccessToken,
                refreshToken,
                new AuthResponse.UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getDiscordId()
                )
        );
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private void storeRefreshToken(UUID userId, String rawToken) {
        String tokenHash = hashToken(rawToken);

        io.jsonwebtoken.Claims claims = jwtService.validateToken(rawToken);
        Instant expiresAt = claims.getExpiration().toInstant();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(expiresAt);
        refreshTokenRepository.save(refreshToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
