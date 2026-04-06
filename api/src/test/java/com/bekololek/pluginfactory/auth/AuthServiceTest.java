package com.bekololek.pluginfactory.auth;

import com.bekololek.pluginfactory.auth.dto.AuthResponse;
import com.bekololek.pluginfactory.auth.dto.DiscordTokenResponse;
import com.bekololek.pluginfactory.auth.dto.DiscordUserInfo;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionRepository;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private DiscordOAuthService discordOAuthService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private DiscordTokenResponse discordTokenResponse;
    private DiscordUserInfo discordUserInfo;

    @BeforeEach
    void setUp() {
        discordTokenResponse = new DiscordTokenResponse(
                "discord-access-token", "Bearer", 604800, "discord-refresh-token", "identify email");
        discordUserInfo = new DiscordUserInfo("123456789", "TestUser", "test@example.com", "avatar-hash");
    }

    @Test
    void handleDiscordCallback_newUser() {
        when(discordOAuthService.exchangeCode("test-code")).thenReturn(discordTokenResponse);
        when(discordOAuthService.fetchDiscordUser("discord-access-token")).thenReturn(discordUserInfo);
        when(userRepository.findByDiscordId("123456789")).thenReturn(Optional.empty());

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("test@example.com");
        savedUser.setDisplayName("TestUser");
        savedUser.setDiscordId("123456789");
        savedUser.setAuthProvider("discord");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");

        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(604800)));
        when(jwtService.validateToken("refresh-token")).thenReturn(claims);

        AuthResponse response = authService.handleDiscordCallback("test-code", "test-state");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("test@example.com");
        assertThat(response.user().displayName()).isEqualTo("TestUser");
        assertThat(response.user().discordId()).isEqualTo("123456789");

        // Verify subscription was created for new user
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getTier()).isEqualTo(Tier.FREE);

        // Verify refresh token was stored
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void handleDiscordCallback_existingUser() {
        when(discordOAuthService.exchangeCode("test-code")).thenReturn(discordTokenResponse);
        when(discordOAuthService.fetchDiscordUser("discord-access-token")).thenReturn(discordUserInfo);

        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");
        existingUser.setDisplayName("TestUser");
        existingUser.setDiscordId("123456789");
        existingUser.setAuthProvider("discord");
        when(userRepository.findByDiscordId("123456789")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");

        Claims claims = mock(Claims.class);
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(604800)));
        when(jwtService.validateToken("refresh-token")).thenReturn(claims);

        AuthResponse response = authService.handleDiscordCallback("test-code", "test-state");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo("test@example.com");

        // Verify NO new subscription was created for existing user
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void refreshAccessToken_success() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUserId(userId);
        storedToken.setTokenHash("somehashvalue");
        storedToken.setExpiresAt(Instant.now().plusSeconds(604800));
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setDisplayName("TestUser");
        user.setDiscordId("123456789");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("new-access-token");

        AuthResponse response = authService.refreshAccessToken(refreshToken);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo(refreshToken);
        assertThat(response.user().id()).isEqualTo(userId);
    }

    @Test
    void refreshAccessToken_revokedToken() {
        String refreshToken = "revoked-refresh-token";

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUserId(UUID.randomUUID());
        storedToken.setExpiresAt(Instant.now().plusSeconds(604800));
        storedToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void refreshAccessToken_expiredToken() {
        String refreshToken = "expired-refresh-token";

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUserId(UUID.randomUUID());
        storedToken.setExpiresAt(Instant.now().minusSeconds(3600));
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refreshAccessToken_invalidToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void logout() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenRepository).deleteByUserId(userId);
    }
}
