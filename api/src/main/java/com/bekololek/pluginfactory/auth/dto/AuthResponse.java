package com.bekololek.pluginfactory.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String email,
            String displayName,
            String discordId
    ) {
    }
}
