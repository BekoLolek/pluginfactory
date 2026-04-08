package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.common.exception.BudgetExhaustedException;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IterationServiceTest {

    @Mock
    private BuildSessionService buildSessionService;

    @Mock
    private BuildIterationRepository buildIterationRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private TokenBudgetService tokenBudgetService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private BuildPipelineService buildPipelineService;

    @InjectMocks
    private IterationService iterationService;

    private UUID sessionId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void requestIteration_happyPath_createsIterationAndCallsPipeline() {
        // Arrange
        BuildSession session = new BuildSession();
        session.setUserId(userId);
        session.setStatus(BuildStatus.COMPLETED);
        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);

        // PRO tier has maxIterations = 5; only 1 existing iteration
        BuildIteration existingIteration = new BuildIteration();
        existingIteration.setIterationNumber(1);
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of(existingIteration))
                .thenReturn(List.of(existingIteration, createCompletedIteration(2)));

        TokenBudget budget = new TokenBudget();
        budget.setAllocatedTokens(500_000);
        budget.setConsumedTokens(100_000); // 80% remaining > 20%
        when(tokenBudgetService.getRemainingBudget(sessionId)).thenReturn(budget);

        when(buildSessionService.updateStatus(sessionId, BuildStatus.BUILDING)).thenReturn(session);

        // Act
        BuildIteration result = iterationService.requestIteration(sessionId, userId, "Add a /heal command");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getIterationNumber()).isEqualTo(2);
        verify(chatMessageService).addMessage(eq(sessionId), eq("user"), eq("Add a /heal command"), eq(null), eq(0));
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.BUILDING);
        verify(buildPipelineService).executeBuild(sessionId);
    }

    @Test
    void requestIteration_iterationLimitReached_throwsForbiddenException() {
        // Arrange - FREE tier has maxIterations = 1
        BuildSession session = new BuildSession();
        session.setUserId(userId);
        session.setStatus(BuildStatus.COMPLETED);
        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.FREE);

        // FREE tier maxIterations = 1, with 1 existing iteration the limit is reached
        BuildIteration existing = new BuildIteration();
        existing.setIterationNumber(1);
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> iterationService.requestIteration(sessionId, userId, "Fix bug"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Iteration limit reached for your tier");
    }

    @Test
    void requestIteration_budgetExhausted_throwsBudgetExhaustedException() {
        // Arrange
        BuildSession session = new BuildSession();
        session.setUserId(userId);
        session.setStatus(BuildStatus.COMPLETED);
        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);
        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);

        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of(new BuildIteration()));

        TokenBudget budget = new TokenBudget();
        budget.setAllocatedTokens(500_000);
        budget.setConsumedTokens(450_000); // only 10% remaining < 20%
        when(tokenBudgetService.getRemainingBudget(sessionId)).thenReturn(budget);

        // Act & Assert
        assertThatThrownBy(() -> iterationService.requestIteration(sessionId, userId, "More features"))
                .isInstanceOf(BudgetExhaustedException.class)
                .hasMessage("Insufficient token budget for iteration");
    }

    @Test
    void requestIteration_sessionNotCompleted_throwsForbiddenException() {
        // Arrange
        BuildSession session = new BuildSession();
        session.setUserId(userId);
        session.setStatus(BuildStatus.BUILDING);
        when(buildSessionService.getSession(sessionId, userId)).thenReturn(session);

        // Act & Assert
        assertThatThrownBy(() -> iterationService.requestIteration(sessionId, userId, "Fix bug"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Session must be in COMPLETED status to iterate");
    }

    @Test
    void listIterations_returnsIterationsInOrder() {
        // Arrange
        BuildIteration it1 = new BuildIteration();
        it1.setIterationNumber(1);
        BuildIteration it2 = new BuildIteration();
        it2.setIterationNumber(2);
        when(buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId))
                .thenReturn(List.of(it1, it2));

        // Act
        List<BuildIteration> result = iterationService.listIterations(sessionId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getIterationNumber()).isEqualTo(1);
        assertThat(result.get(1).getIterationNumber()).isEqualTo(2);
    }

    private BuildIteration createCompletedIteration(int number) {
        BuildIteration iteration = new BuildIteration();
        iteration.setId(UUID.randomUUID());
        iteration.setSessionId(sessionId);
        iteration.setIterationNumber(number);
        iteration.setStatus("COMPLETED");
        iteration.setTrigger("USER_FEEDBACK");
        return iteration;
    }
}
