package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.container.ContainerSession;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles the persistent build state with reality on every application
 * startup.
 *
 * <h2>Why this exists</h2>
 * {@link BuildPipelineService#executeBuild(UUID)} runs synchronously in
 * whatever thread invoked it — today that's an HTTP request thread from
 * {@code /api/v1/builds/{id}/iterate}. There is no durable job queue,
 * no heartbeat, and no checkpointing: once the pipeline is mid-flight,
 * the only thing keeping it alive is the JVM. If the process dies
 * (deploy, OOM, host reboot, crash), the build session's row stays
 * frozen in whatever transient status it was in — typically
 * {@code PLANNING}, {@code APPROVED}, {@code BUILDING}, or
 * {@code TESTING} — and the frontend polls forever because nothing
 * will ever transition it out.
 *
 * <h2>What this does</h2>
 * On {@link ApplicationReadyEvent}, sweep every session in a transient
 * status and fail it cleanly:
 * <ul>
 *   <li>session status → {@link BuildStatus#FAILED},
 *       phase → {@link BuildPhase#IDLE},
 *       {@code completedAt} → now</li>
 *   <li>any in-flight {@link BuildIteration} (status {@code RUNNING})
 *       → {@code FAILED} with {@code completedAt}</li>
 *   <li>a {@link BuildError} row is written against the latest iteration
 *       so the user can see what happened in the UI</li>
 *   <li>any {@link ContainerSession} that never had {@code releasedAt}
 *       stamped gets stamped now — the Docker container itself is gone
 *       (it died with the JVM or was reaped by the pool manager on
 *       restart), so the row must not be left claimed</li>
 * </ul>
 *
 * <h2>What this does NOT do</h2>
 * Resume mid-flight builds from where they crashed. That would require
 * checkpointing intermediate artifacts (generated source, in-container
 * compile results) to durable storage, which we don't currently do.
 * The safe-and-simple behavior is to fail recovered sessions and let
 * the user start a new build. A session that gets this far has already
 * consumed part of its token budget; we intentionally do NOT refund
 * those tokens, because the LLM calls really did happen.
 *
 * <h2>Future work</h2>
 * The real fix is to move the build pipeline off the request thread and
 * onto a persistent queue (DB-backed outbox, Quartz, or a proper job
 * runner) with idempotent phase transitions so a crashed build can
 * resume at its last checkpoint. Until then, this recovery sweep
 * prevents the "stuck forever" UX.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BuildRecoveryService {

    /**
     * Statuses that indicate "the pipeline is actively working on this
     * session right now". A session in any of these states at startup
     * time is by definition abandoned — the only thing that could have
     * been driving it was the previous JVM, and that JVM is gone.
     *
     * {@code CHATTING} is NOT in this list: it's a user-driven state,
     * not pipeline-driven, so the session is perfectly fine to resume
     * after restart — the user just keeps typing.
     *
     * {@code COMPLETED}, {@code FAILED}, and {@code CANCELLED} are
     * terminal states and also excluded.
     */
    private static final List<BuildStatus> TRANSIENT_STATUSES = List.of(
            BuildStatus.PLANNING,
            BuildStatus.APPROVED,
            BuildStatus.BUILDING,
            BuildStatus.TESTING
    );

    private static final String RESTART_RECOVERY_MESSAGE =
            "This build was interrupted because the Plugin Factory server " +
                    "restarted mid-build. The session has been marked as failed. " +
                    "Please start a new build — any LLM tokens already spent on " +
                    "this session have been charged to your plan.";

    private static final String STALE_RECOVERY_MESSAGE =
            "This build was marked as failed because no progress was reported " +
                    "for more than 15 minutes. The pipeline worker likely died or " +
                    "got stuck. Please start a new build — any LLM tokens already " +
                    "spent on this session have been charged to your plan.";

    /**
     * How long a session can sit in a transient status without any
     * {@code updated_at} bump before the reaper considers it stuck.
     *
     * <p>Every phase transition saves the session entity, which Hibernate
     * stamps via {@code BaseEntity.@PreUpdate}, so this doubles as a
     * free heartbeat. The longest legitimate gap between phase
     * transitions is the Maven compile in COMPILATION (a few minutes
     * for a large plugin) plus the LLM call in IMPLEMENTATION (also
     * a few minutes). Fifteen minutes gives plenty of headroom while
     * still catching stuck workers within a reasonable time.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(15);

    /**
     * How often the reaper runs. Every two minutes is plenty granular
     * for a 15-minute staleness threshold and avoids hammering the DB.
     */
    private static final long REAPER_INTERVAL_MS = 120_000L;

    private final BuildSessionRepository buildSessionRepository;
    private final BuildIterationRepository buildIterationRepository;
    private final BuildErrorRepository buildErrorRepository;
    private final ContainerSessionRepository containerSessionRepository;
    private final SubscriptionService subscriptionService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedBuilds() {
        List<BuildSession> interrupted = buildSessionRepository.findByStatusIn(TRANSIENT_STATUSES);
        if (interrupted.isEmpty()) {
            log.info("Startup build recovery: no interrupted sessions found.");
        } else {
            log.warn(
                    "Startup build recovery: found {} interrupted build session(s) in transient state. " +
                            "Marking them FAILED.",
                    interrupted.size()
            );
            Instant now = Instant.now();
            for (BuildSession session : interrupted) {
                recoverSession(session, now, RESTART_RECOVERY_MESSAGE);
            }
        }

        // Container sessions whose release was never recorded are leaks
        // even if the owning iteration was already closed — we reap them
        // independently so the container_sessions table doesn't grow
        // claimed rows forever. The actual Docker containers are already
        // gone at this point; this is purely a DB-state fix.
        reapOpenContainerSessions("Startup");
    }

    /**
     * Periodic backstop for the startup recovery sweep.
     *
     * <p>Catches the failure modes the startup hook can't see: a worker
     * thread that died mid-build without crashing the JVM, a hung
     * Docker exec, an LLM call that wedged behind a half-broken TCP
     * connection, etc. The session row in those cases is left in a
     * transient state with no further phase transitions, so we detect
     * it by looking for sessions whose {@code updated_at} hasn't moved
     * in {@link #STALE_THRESHOLD}.
     *
     * <p>This is the second half of the heartbeat-and-reap pattern.
     * The "heartbeat" is implicit — every {@code updateStatus} /
     * {@code updatePhase} call from the pipeline saves the entity,
     * which trips Hibernate's {@code @PreUpdate} on
     * {@code BaseEntity.updatedAt}. As long as the pipeline is making
     * progress, the timestamp moves and the row never qualifies for
     * reaping. The moment progress stops, the clock starts running.
     */
    @Scheduled(fixedDelay = REAPER_INTERVAL_MS, initialDelay = REAPER_INTERVAL_MS)
    @Transactional
    public void reapStaleBuilds() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD);
        List<BuildSession> stale = buildSessionRepository
                .findByStatusInAndUpdatedAtBefore(TRANSIENT_STATUSES, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.warn(
                "Stale build reaper: found {} session(s) in transient state with no progress for >{} minutes. " +
                        "Marking them FAILED.",
                stale.size(), STALE_THRESHOLD.toMinutes()
        );
        Instant now = Instant.now();
        for (BuildSession session : stale) {
            recoverSession(session, now, STALE_RECOVERY_MESSAGE);
        }
        reapOpenContainerSessions("Reaper");
    }

    private void reapOpenContainerSessions(String label) {
        List<ContainerSession> openContainers = containerSessionRepository.findByReleasedAtIsNull();
        if (openContainers.isEmpty()) {
            return;
        }
        log.warn(
                "{} container reap: found {} container_sessions with no released_at. Closing them.",
                label, openContainers.size()
        );
        Instant now = Instant.now();
        for (ContainerSession cs : openContainers) {
            cs.setReleasedAt(now);
        }
        containerSessionRepository.saveAll(openContainers);
    }

    private void recoverSession(BuildSession session, Instant now, String reason) {
        UUID sessionId = session.getId();
        BuildStatus previousStatus = session.getStatus();
        BuildPhase previousPhase = session.getCurrentPhase();

        session.setStatus(BuildStatus.FAILED);
        session.setCurrentPhase(BuildPhase.IDLE);
        session.setCompletedAt(now);
        buildSessionRepository.save(session);

        // Close any running iterations for this session so the progress
        // UI doesn't render an orphaned "running" iteration banner.
        List<BuildIteration> iterations = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId);
        BuildIteration latest = null;
        for (BuildIteration iteration : iterations) {
            if ("RUNNING".equals(iteration.getStatus())) {
                iteration.setStatus("FAILED");
                iteration.setCompletedAt(now);
                buildIterationRepository.save(iteration);
            }
            latest = iteration;
        }

        // Attach a BuildError so the admin error endpoints have something
        // concrete to show. Since V13, session_id is the required link and
        // iteration_id is optional, so we can record reaper kills for
        // sessions that died pre-iteration (e.g. stuck in PLANNING) too —
        // those used to be invisible.
        BuildError error = new BuildError();
        error.setSessionId(sessionId);
        if (latest != null) {
            error.setIterationId(latest.getId());
        }
        error.setCategory("SYSTEM");
        error.setSeverity("ERROR");
        error.setMessage(reason);
        error.setRetryCount(0);
        buildErrorRepository.save(error);

        // Refund the build slot. We bypass BuildSessionService.updateStatus
        // because recovery needs to set status + phase + completedAt
        // atomically and write a BuildError, so we have to invoke the
        // refund explicitly here. The session was filtered into this
        // method by TRANSIENT_STATUSES, so previousStatus is by
        // construction non-terminal — an unconditional refund is safe and
        // refundBuildSlot itself clamps at 0 as a final safety net.
        subscriptionService.refundBuildSlot(session.getUserId());

        log.info(
                "Build recovery: session {} recovered (was {}/{}).",
                sessionId, previousStatus, previousPhase
        );
    }
}
