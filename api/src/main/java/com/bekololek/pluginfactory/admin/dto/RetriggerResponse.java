package com.bekololek.pluginfactory.admin.dto;

import java.util.UUID;

/**
 * Outcome of an admin retrigger call. The action describes what state
 * the session was put back into:
 * <ul>
 *   <li>{@code PIPELINE_RESTARTED} — session had a plan and at least
 *       one iteration; we re-ran the implementation/compile pipeline
 *       (same path as the existing {@code /recover} endpoint). A new
 *       iteration was created.</li>
 *   <li>{@code RESET_TO_PLAN_REVIEW} — session had a plan but no
 *       iterations (typical of the PLAN_REVIEW reaper bug). Status
 *       and phase are reset so the user can approve the plan and
 *       drive the build forward themselves.</li>
 *   <li>{@code RESET_TO_CLARIFICATION} — no plan was ever generated.
 *       Session is reset to CHATTING/CLARIFICATION so the user can
 *       resume the conversation.</li>
 * </ul>
 */
public record RetriggerResponse(
        UUID sessionId,
        String action,
        String status,
        String currentPhase,
        Integer iterationNumber
) {}
