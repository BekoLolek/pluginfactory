package com.bekololek.pluginfactory.plan.dto;

/**
 * Returned (HTTP 422) when plan approval is blocked because the estimated build
 * cost exceeds the user's remaining token budget.
 */
public record BudgetFeasibilityDto(TokenEstimate estimate, String message) {}
