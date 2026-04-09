package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Synchronously creates a {@link BuildIteration} row and hands the
 * session off to the async build pipeline.
 *
 * <h2>Why this is a separate bean</h2>
 * The pipeline body lives on {@link BuildPipelineService#executeBuild(UUID, UUID)}
 * and is annotated with {@code @Async}. Spring's async proxying only
 * fires when the call crosses a bean boundary — self-invocation within
 * the same bean (e.g. {@code this.executeBuild(...)}) silently runs
 * synchronously on the calling thread, defeating the whole point of
 * making it async. Putting the iteration-creation logic on its own
 * service guarantees the call to {@code executeBuild} goes through
 * the Spring proxy and lands on the dedicated build executor.
 *
 * <h2>What it gives callers</h2>
 * A real, persisted {@link BuildIteration} that the caller can return
 * to the client immediately — no DB race against the worker, no
 * "iteration appears later" gap in the API contract. The async worker
 * picks up the same row by ID and updates its status as it makes
 * progress.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BuildLauncher {

    private final BuildIterationRepository buildIterationRepository;
    private final BuildPipelineService buildPipelineService;

    /**
     * Persists a new {@link BuildIteration} for {@code sessionId} and
     * triggers the async pipeline. Returns immediately with the
     * persisted row; the actual build runs on the
     * {@code buildPipelineExecutor} pool.
     *
     * @param sessionId the build session
     * @param trigger   free-form reason — typically {@code INITIAL}
     *                  for a first build after plan approval, or
     *                  {@code MANUAL_ITERATION} for a user-requested
     *                  iteration from a completed build
     * @return the freshly-persisted iteration row
     */
    public BuildIteration startBuild(UUID sessionId, String trigger) {
        int iterationNumber = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId).size() + 1;

        BuildIteration iteration = new BuildIteration();
        iteration.setSessionId(sessionId);
        iteration.setIterationNumber(iterationNumber);
        iteration.setStatus("RUNNING");
        iteration.setTrigger(trigger);
        iteration = buildIterationRepository.save(iteration);

        log.info(
                "Launching build for session {} (iteration {}, trigger {})",
                sessionId, iteration.getIterationNumber(), trigger
        );

        // Crosses the Spring proxy boundary, so @Async fires and this
        // returns immediately. The worker picks up the iteration row
        // by ID inside its own transaction.
        buildPipelineService.executeBuild(sessionId, iteration.getId());

        return iteration;
    }
}
