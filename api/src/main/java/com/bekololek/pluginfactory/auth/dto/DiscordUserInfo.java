package com.bekololek.pluginfactory.auth.dto;

public record DiscordUserInfo(
        String id,
        String username,
        String email,
        String avatar
) {
}
