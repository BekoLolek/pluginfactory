package com.bekololek.pluginfactory.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class ModelRouterTest {

    private final ModelRouter modelRouter = new ModelRouter();

    @Test
    void clarificationUsesHaiku() {
        assertEquals("claude-haiku-4-5-20250929",
                modelRouter.selectModel(ModelRouter.TaskType.CLARIFICATION));
    }

    @Test
    void inputValidationUsesHaiku() {
        assertEquals("claude-haiku-4-5-20250929",
                modelRouter.selectModel(ModelRouter.TaskType.INPUT_VALIDATION));
    }

    @Test
    void errorClassificationUsesHaiku() {
        assertEquals("claude-haiku-4-5-20250929",
                modelRouter.selectModel(ModelRouter.TaskType.ERROR_CLASSIFICATION));
    }

    @Test
    void complexityEstimationUsesSonnet() {
        assertEquals("claude-sonnet-4-5-20250514",
                modelRouter.selectModel(ModelRouter.TaskType.COMPLEXITY_ESTIMATION));
    }

    @Test
    void planGenerationUsesSonnet() {
        assertEquals("claude-sonnet-4-5-20250514",
                modelRouter.selectModel(ModelRouter.TaskType.PLAN_GENERATION));
    }

    @Test
    void codeGenerationUsesSonnet() {
        assertEquals("claude-sonnet-4-5-20250514",
                modelRouter.selectModel(ModelRouter.TaskType.CODE_GENERATION));
    }

    @Test
    void testGenerationUsesSonnet() {
        assertEquals("claude-sonnet-4-5-20250514",
                modelRouter.selectModel(ModelRouter.TaskType.TEST_GENERATION));
    }

    @Test
    void securityAnalysisUsesSonnet() {
        assertEquals("claude-sonnet-4-5-20250514",
                modelRouter.selectModel(ModelRouter.TaskType.SECURITY_ANALYSIS));
    }

    @Test
    void clarificationMaxTokens() {
        assertEquals(2048, modelRouter.getMaxTokens(ModelRouter.TaskType.CLARIFICATION));
    }

    @Test
    void inputValidationMaxTokens() {
        assertEquals(1024, modelRouter.getMaxTokens(ModelRouter.TaskType.INPUT_VALIDATION));
    }

    @Test
    void errorClassificationMaxTokens() {
        assertEquals(1024, modelRouter.getMaxTokens(ModelRouter.TaskType.ERROR_CLASSIFICATION));
    }

    @Test
    void complexityEstimationMaxTokens() {
        assertEquals(1024, modelRouter.getMaxTokens(ModelRouter.TaskType.COMPLEXITY_ESTIMATION));
    }

    @Test
    void planGenerationMaxTokens() {
        assertEquals(8192, modelRouter.getMaxTokens(ModelRouter.TaskType.PLAN_GENERATION));
    }

    @Test
    void codeGenerationMaxTokens() {
        assertEquals(16384, modelRouter.getMaxTokens(ModelRouter.TaskType.CODE_GENERATION));
    }

    @Test
    void testGenerationMaxTokens() {
        assertEquals(8192, modelRouter.getMaxTokens(ModelRouter.TaskType.TEST_GENERATION));
    }

    @Test
    void securityAnalysisMaxTokens() {
        assertEquals(4096, modelRouter.getMaxTokens(ModelRouter.TaskType.SECURITY_ANALYSIS));
    }
}
