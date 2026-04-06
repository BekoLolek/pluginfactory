package com.bekololek.pluginfactory.auth;

import com.bekololek.pluginfactory.auth.dto.AuthResponse;
import com.bekololek.pluginfactory.auth.dto.RefreshRequest;
import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/discord")
    public ResponseEntity<Map<String, String>> getDiscordAuthUrl() {
        String url = authService.getDiscordAuthorizationUrl();
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/discord/callback")
    @RateLimiter(name = "auth")
    public ResponseEntity<AuthResponse> handleDiscordCallback(
            @RequestParam String code,
            @RequestParam String state) {
        AuthResponse response = authService.handleDiscordCallback(code, state);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "auth")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        AuthResponse response = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        authService.logout(userId);
        return ResponseEntity.ok().build();
    }
}
