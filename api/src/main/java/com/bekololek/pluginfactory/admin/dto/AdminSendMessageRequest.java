package com.bekololek.pluginfactory.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/v1/admin/builds/{sessionId}/messages — an admin message
 * injected into a user's session as if the user sent it. Mirrors the size cap
 * on the user-facing chat DTO (10,000 chars).
 */
public record AdminSendMessageRequest(
        @NotBlank @Size(max = 10_000) String content) {}
