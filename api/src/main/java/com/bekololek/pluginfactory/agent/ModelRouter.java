package com.bekololek.pluginfactory.agent;

import org.springframework.stereotype.Service;

@Service
public class ModelRouter {

    public enum TaskType {
        CLARIFICATION,
        INPUT_VALIDATION,
        ERROR_CLASSIFICATION,
        COMPLEXITY_ESTIMATION,
        PLAN_GENERATION,
        CODE_GENERATION,
        TEST_GENERATION,
        SECURITY_ANALYSIS
    }

    // Unversioned aliases always resolve to the current snapshot, so we don't
    // have to chase model-release dates every time Anthropic ships a new one.
    private static final String HAIKU = "claude-haiku-4-5";
    private static final String SONNET = "claude-sonnet-4-5";

    public String selectModel(TaskType taskType) {
        return switch (taskType) {
            case CLARIFICATION, INPUT_VALIDATION, ERROR_CLASSIFICATION -> HAIKU;
            case COMPLEXITY_ESTIMATION, PLAN_GENERATION, CODE_GENERATION,
                 TEST_GENERATION, SECURITY_ANALYSIS -> SONNET;
        };
    }

    public int getMaxTokens(TaskType taskType) {
        return switch (taskType) {
            case CLARIFICATION -> 2048;
            case INPUT_VALIDATION, ERROR_CLASSIFICATION, COMPLEXITY_ESTIMATION -> 1024;
            case PLAN_GENERATION, TEST_GENERATION -> 8192;
            // Multi-class plugins (~850+ LOC across 6+ files) overran 16k and
            // truncated the tool output. Sonnet 4.5 supports far higher output.
            case CODE_GENERATION -> 32000;
            case SECURITY_ANALYSIS -> 4096;
        };
    }

    /**
     * Lower temperatures reduce creative latitude and the rate of API
     * hallucinations. Conversational and exploratory tasks keep the
     * default; structured / generative-correctness tasks (code, plans,
     * classification) clamp toward determinism.
     */
    public Double getTemperature(TaskType taskType) {
        return switch (taskType) {
            case CODE_GENERATION -> 0.2;
            case PLAN_GENERATION, TEST_GENERATION -> 0.3;
            case INPUT_VALIDATION, ERROR_CLASSIFICATION,
                 COMPLEXITY_ESTIMATION, SECURITY_ANALYSIS -> 0.0;
            case CLARIFICATION -> null; // server default
        };
    }
}
