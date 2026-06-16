package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Detection of AI-provider-outage failures (distinct from code/build errors). */
class BuildPipelineProviderOutageTest {

    @Test
    void recognisesProviderUnavailableMessages() {
        assertThat(BuildPipelineService.isProviderUnavailable(
                "AI service temporarily unavailable. Please try again in a moment.")).isTrue();
        assertThat(BuildPipelineService.isProviderUnavailable(
                "Anthropic is temporarily unavailable")).isTrue();
    }

    @Test
    void doesNotFlagCodeOrBuildErrors() {
        assertThat(BuildPipelineService.isProviderUnavailable(
                "Maven build failed (exit code 1): cannot find symbol")).isFalse();
        assertThat(BuildPipelineService.isProviderUnavailable(
                "0/5 scenarios passed")).isFalse();
        assertThat(BuildPipelineService.isProviderUnavailable(null)).isFalse();
    }
}
