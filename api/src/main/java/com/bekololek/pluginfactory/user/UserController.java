package com.bekololek.pluginfactory.user;

import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import com.bekololek.pluginfactory.user.dto.ApiKeyCreatedDto;
import com.bekololek.pluginfactory.user.dto.ApiKeyDto;
import com.bekololek.pluginfactory.user.dto.CreateApiKeyRequest;
import com.bekololek.pluginfactory.user.dto.UpdateProfileRequest;
import com.bekololek.pluginfactory.user.dto.UsageStatsDto;
import com.bekololek.pluginfactory.user.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ApiKeyService apiKeyService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        return ResponseEntity.ok(userService.getCurrentUser(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @GetMapping("/me/usage")
    public ResponseEntity<UsageStatsDto> getUsageStats() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        return ResponseEntity.ok(userService.getUsageStats(userId));
    }

    @GetMapping("/me/api-keys")
    public ResponseEntity<List<ApiKeyDto>> listApiKeys() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        return ResponseEntity.ok(apiKeyService.listKeys(userId));
    }

    @PostMapping("/me/api-keys")
    public ResponseEntity<ApiKeyCreatedDto> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.createApiKey(userId, request.name()));
    }

    @DeleteMapping("/me/api-keys/{keyId}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable UUID keyId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        apiKeyService.revokeKey(userId, keyId);
        return ResponseEntity.noContent().build();
    }
}
