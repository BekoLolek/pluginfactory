package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.agent.FunctionalTestAgent;
import com.bekololek.pluginfactory.agent.ImplementerAgent;
import com.bekololek.pluginfactory.agent.dto.ImplementationResult;
import com.bekololek.pluginfactory.email.EmailNotificationService;
import com.bekololek.pluginfactory.common.config.AsyncConfig;
import com.bekololek.pluginfactory.common.logging.MdcRequestFilter;
import org.slf4j.MDC;
import com.bekololek.pluginfactory.container.ContainerPoolManager;
import com.bekololek.pluginfactory.container.ContainerSession;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.container.DockerService;
import com.bekololek.pluginfactory.container.ExecResult;
import com.bekololek.pluginfactory.container.FunctionalTestService;
import com.bekololek.pluginfactory.container.TestServerService;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class BuildPipelineService {

    private final ImplementerAgent implementerAgent;
    private final DockerService dockerService;
    private final ContainerPoolManager containerPoolManager;
    private final BuildSessionService buildSessionService;
    private final BuildProgressService buildProgressService;
    private final TokenBudgetService tokenBudgetService;
    private final ArtifactService artifactService;
    private final SecurityScanService securityScanService;
    private final BuildIterationRepository buildIterationRepository;
    private final BuildErrorRepository buildErrorRepository;
    private final ContainerSessionRepository containerSessionRepository;
    private final ErrorClassifier errorClassifier;
    private final RetryPolicy retryPolicy;
    private final ChatMessageService chatMessageService;
    private final EmailNotificationService emailNotificationService;
    private final TestServerService testServerService;
    private final FunctionalTestAgent functionalTestAgent;
    private final FunctionalTestService functionalTestService;
    private final SubscriptionService subscriptionService;
    private final BuildLogRecorder buildLogRecorder;

    /**
     * Hard cap on automatic fix-retries per session, independent of the token
     * budget. Without it, a non-converging compile/functional loop on a
     * high-budget (e.g. TEAM) session would iterate dozens of times.
     */
    private static final int MAX_AUTO_RETRIES = 4;

    /**
     * Runs the full build pipeline off the request thread.
     *
     * <p>Annotated with {@link Async} and bound to the dedicated
     * {@link AsyncConfig#BUILD_EXECUTOR} pool so HTTP threads never
     * block on a multi-minute Maven compile and so concurrency is
     * bounded to a sane number regardless of caller churn.
     *
     * <p>IMPORTANT: because this method is async, any exception that
     * escapes here is invisible to the caller. We therefore wrap the
     * entire body in a catch-all that converts unexpected failures
     * into a {@code FAILED} session status — without this, an
     * unhandled NPE or runtime error would leave the session stuck
     * in {@code BUILDING} exactly like the restart scenario we're
     * trying to avoid.
     *
     * <p>Prefer calling {@code BuildLauncher.startBuild(...)} instead
     * of this method directly so you get a synchronously-created
     * iteration row to return to the client.
     */
    @Async(AsyncConfig.BUILD_EXECUTOR)
    public void executeBuild(UUID sessionId, UUID iterationId) {
        MDC.put(MdcRequestFilter.SESSION_ID, sessionId.toString());
        try {
            UUID ownerId = buildSessionService.getSessionById(sessionId).getUserId();
            MDC.put(MdcRequestFilter.OWNER_ID, ownerId.toString());
        } catch (Exception ignored) {
            // Owner is best-effort for log correlation; never fail the build over it.
        }
        try {
            executeBuildInternal(sessionId, iterationId);
        } catch (Throwable t) {
            // Defense in depth: executeBuildInternal already catches
            // Exception and marks the session FAILED via
            // handleBuildError. This outer catch exists for Errors
            // and anything else that slipped past — we never want an
            // async build to silently leave a session in BUILDING.
            log.error("Unhandled error in async build for session {}", sessionId, t);
            try {
                buildSessionService.updateStatus(sessionId, BuildStatus.FAILED);
                buildSessionService.updatePhase(sessionId, BuildPhase.IDLE);
                buildProgressService.notifyStatusChange(sessionId, BuildStatus.FAILED);
                buildProgressService.notifyError(sessionId,
                        "Build crashed unexpectedly: " + t.getMessage());
            } catch (Exception secondary) {
                log.error("Failed to mark session {} as FAILED after crash", sessionId, secondary);
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
        } finally {
            MDC.remove(MdcRequestFilter.SESSION_ID);
            MDC.remove(MdcRequestFilter.OWNER_ID);
        }
    }

    private void executeBuildInternal(UUID sessionId, UUID iterationId) {
        // Take ownership of the status lifecycle: no matter what status
        // the caller left the session in (APPROVED from approvePlan or
        // BUILDING from iterate), we move it into BUILDING here so the
        // whole pipeline has a single, predictable starting state. This
        // also doubles as the "worker picked up the job" signal for
        // clients that poll session status.
        buildSessionService.updateStatus(sessionId, BuildStatus.BUILDING);
        buildProgressService.notifyStatusChange(sessionId, BuildStatus.BUILDING);

        BuildIteration iteration = buildIterationRepository.findById(iterationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Iteration " + iterationId + " not found for session " + sessionId));

        // Hoisted so the catch blocks can pass the failed source into the
        // auto-fix retry (targeted repair instead of a blind re-roll).
        Map<String, String> files = null;
        try {
            // Phase 1: IMPLEMENTATION
            buildSessionService.updatePhase(sessionId, BuildPhase.IMPLEMENTATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.IMPLEMENTATION);

            ImplementationResult result = implementerAgent.implement(sessionId);
            files = result.files();
            tokenBudgetService.consumeTokens(sessionId, "implementation", result.tokensUsed());

            // Phase 2: COMPILATION
            buildSessionService.updatePhase(sessionId, BuildPhase.COMPILATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.COMPILATION);

            byte[] jarBytes = compileInContainer(sessionId, iterationId, files);

            // Phases 3-6: security scan, runtime smoke test, functional test, deliver.
            finalizeBuild(sessionId, iteration, files, jarBytes);

        } catch (CompilationException e) {
            log.error("Compilation failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "COMPILATION", e.getMessage(), e, files);

        } catch (RuntimeTestException e) {
            log.error("Runtime test failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "RUNTIME", e.getMessage(), e, files);

        } catch (FunctionalTestException e) {
            log.error("Functional test failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "FUNCTIONAL", e.getMessage(), e, files);

        } catch (SecurityViolationException e) {
            log.error("Security scan failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "SECURITY", e.getMessage(), e, files);

        } catch (Exception e) {
            log.error("Build failed for session {}: {}", sessionId, e.getMessage(), e);
            handleBuildError(iteration, sessionId, "GENERAL", e.getMessage(), e, files);
        }
    }

    /**
     * Shared build tail: security scan -> runtime smoke test -> deliver ->
     * mark complete. Used by both the first build and auto-retries so neither
     * can skip the smoke test. Throws {@link SecurityViolationException} or
     * {@link RuntimeTestException} which the callers route to handleBuildError.
     */
    private void finalizeBuild(UUID sessionId, BuildIteration iteration,
                               Map<String, String> files, byte[] jarBytes) {
        // SECURITY_SCAN
        buildSessionService.updatePhase(sessionId, BuildPhase.SECURITY_SCAN);
        buildProgressService.notifyPhaseChange(sessionId, BuildPhase.SECURITY_SCAN);
        String allSource = concatenateSource(files);
        SecurityScanResult scanResult = securityScanService.scanSource(allSource);
        if (!scanResult.passed()) {
            throw new SecurityViolationException(scanResult.violations());
        }

        // INTEGRATION_TEST — load the JAR on a real Paper server and confirm
        // it enables cleanly. Catches runtime failures compilation misses.
        buildSessionService.updatePhase(sessionId, BuildPhase.INTEGRATION_TEST);
        buildProgressService.notifyPhaseChange(sessionId, BuildPhase.INTEGRATION_TEST);
        String pluginName = pluginNameFor(files);
        TestServerService.SmokeResult smoke = testServerService.runSmokeTest(
                jarBytes, pluginName, sessionId, iteration.getId());
        if (!smoke.passed()) {
            throw new RuntimeTestException(smoke.detail());
        }
        log.info("Runtime smoke test passed for session {}: {}", sessionId, smoke.detail());

        // FUNCTIONAL TEST (Basic+ tier): an in-server bot drives the plugin's
        // commands/interactions and asserts observable effects. Free tier keeps
        // the enable-check only. Failures throw FunctionalTestException, which
        // the auto-fix loop treats as recoverable.
        UUID ownerId = buildSessionService.getSessionById(sessionId).getUserId();
        if (subscriptionService.getTierForUser(ownerId) != Tier.FREE) {
            FunctionalTestAgent.ScenarioScript script = functionalTestAgent.generate(sessionId, files);
            tokenBudgetService.consumeTokens(sessionId, "testing", script.tokensUsed());
            FunctionalTestService.FunctionalResult fr =
                    functionalTestService.run(jarBytes, pluginName, script.script(),
                            sessionId, iteration.getId());
            // User-facing summary only — counts + failed check NAMES, never the
            // raw bot/assertion output (which goes to the repair context below).
            chatMessageService.addMessage(sessionId, "system", friendlyFunctionalSummary(fr), null, 0);
            if (fr.ran() && !fr.passed()) {
                throw new FunctionalTestException(fr.detail());
            }
            log.info("Functional test for session {} ({}): {}", sessionId,
                    fr.ran() ? "ran" : "skipped", fr.detail());
        }

        // DELIVERING
        buildSessionService.updatePhase(sessionId, BuildPhase.DELIVERING);
        buildProgressService.notifyPhaseChange(sessionId, BuildPhase.DELIVERING);
        byte[] sourceZipBytes = createSourceZip(files);
        String pluginYml = extractPluginYml(files);
        artifactService.storeArtifact(sessionId, iteration.getId(), jarBytes, sourceZipBytes,
                pluginYml, scanResult.passed());

        iteration.setStatus("COMPLETED");
        iteration.setCompletedAt(Instant.now());
        buildIterationRepository.save(iteration);

        buildSessionService.updateStatus(sessionId, BuildStatus.COMPLETED);
        buildSessionService.updatePhase(sessionId, BuildPhase.IDLE);
        buildProgressService.notifyStatusChange(sessionId, BuildStatus.COMPLETED);
        emailNotificationService.notifyBuildSuccess(sessionId);

        log.info("Build completed successfully for session {}", sessionId);
    }

    /** A user-friendly one-liner about the functional test — no raw bot output. */
    private String friendlyFunctionalSummary(FunctionalTestService.FunctionalResult fr) {
        if (!fr.ran()) {
            return "🧪 Functional test skipped.";
        }
        int total = fr.scenarios().size();
        long passed = fr.scenarios().stream().filter(FunctionalTestService.ScenarioResult::passed).count();
        if (fr.passed()) {
            return "✅ Functional test: " + passed + "/" + total + " checks passed.";
        }
        String failed = fr.scenarios().stream()
                .filter(s -> !s.passed())
                .map(FunctionalTestService.ScenarioResult::name)
                .reduce((a, b) -> a + "; " + b).orElse("");
        return "🧪 Functional test: " + passed + "/" + total
                + " checks passed — automatically retrying to fix: " + failed;
    }

    /** Plugin name from the generated plugin.yml (for the smoke-test log match). */
    private String pluginNameFor(Map<String, String> files) {
        String yml = extractPluginYml(files);
        if (yml != null) {
            for (String line : yml.split("\n")) {
                String t = line.trim();
                if (t.startsWith("name:")) {
                    return t.substring(5).trim().replace("\"", "").replace("'", "");
                }
            }
        }
        return "plugin";
    }

    private byte[] compileInContainer(UUID sessionId, UUID iterationId,
                                       Map<String, String> files) {
        // Claim a container whose workspace is verified empty. Pooled build
        // containers are reused across sessions; a clean-or-discard claim
        // prevents a previous build's generated sources from compiling into
        // this one (cross-session contamination).
        String containerId = containerPoolManager.claimCleanBuildContainer();

        // Track container session
        ContainerSession containerSession = new ContainerSession();
        containerSession.setIterationId(iterationId);
        containerSession.setContainerId(containerId);
        containerSession.setContainerType("BUILD");
        containerSession.setMemoryMb(3072);
        containerSession.setCpuMillicores(1000);
        containerSessionRepository.save(containerSession);

        try {
            // Workspace is already verified empty by claimCleanBuildContainer()
            // (wiped as root, then checked), so we can copy straight in without
            // risking leftover sources from a previous pooled build.
            byte[] tarArchive = createTarArchive(files);
            dockerService.copyToContainer(containerId, tarArchive, "/plugin-workspace");

            // Run Maven build — cap JVM heap to leave room for OS/native overhead
            ExecResult buildResult = dockerService.executeCommand(containerId,
                    "sh", "-c", "cd /plugin-workspace && MAVEN_OPTS='-Xmx1536m -Xms256m' mvn clean package -q -DskipTests");

            // Persist the full compile output (pass or fail) for the dashboard.
            buildLogRecorder.record(sessionId, iterationId, "COMPILATION",
                    buildResult.exitCode(), buildResult.stdout() + "\n" + buildResult.stderr());

            if (buildResult.exitCode() != 0) {
                throw new CompilationException(
                        "Maven build failed (exit code " + buildResult.exitCode() + "): " +
                                buildResult.stderr() + buildResult.stdout());
            }

            // Locate the shaded plugin jar (largest, excluding the pre-shade
            // original-*, -sources, and -javadoc jars) and copy just that file.
            // Copying a single file yields a one-entry tar, which we read
            // without commons-compress's multi-entry skip path. Previously the
            // whole target/ tar was stored verbatim as "plugin.jar" — a tarball,
            // not a loadable jar.
            ExecResult find = dockerService.executeCommand(containerId, "sh", "-c",
                    "ls -S /plugin-workspace/target/*.jar 2>/dev/null "
                            + "| grep -vE 'original-|-sources\\.jar|-javadoc\\.jar' | head -n1");
            String jarPath = find.stdout().trim();
            if (jarPath.isEmpty()) {
                throw new CompilationException(
                        "Build succeeded but produced no plugin JAR in target/.");
            }
            byte[] jar = firstTarEntryBytes(dockerService.copyFromContainer(containerId, jarPath));
            if (jar == null) {
                throw new CompilationException("Could not read the produced plugin JAR.");
            }
            return jar;

        } finally {
            // Release container back to pool
            containerSession.setReleasedAt(Instant.now());
            containerSessionRepository.save(containerSession);
            containerPoolManager.releaseContainer(containerId, DockerService.ContainerType.BUILD);
        }
    }

    private void handleBuildError(BuildIteration iteration, UUID sessionId,
                                   String category, String message, Exception e,
                                   Map<String, String> previousFiles) {
        ErrorClassifier.ErrorCategory classified = errorClassifier.classify(message);
        // Compilation, runtime, and functional failures are all code problems the
        // auto-fix loop can attempt to repair (it gets the previous source + the
        // exact error). Treat them as recoverable so the targeted repair runs,
        // rather than giving up on a strict STRUCTURAL classification. SECURITY
        // and other categories keep the classifier's verdict.
        ErrorClassifier.ErrorCategory retryCategory =
                ("FUNCTIONAL".equals(category) || "RUNTIME".equals(category)
                        || "COMPILATION".equals(category))
                        ? ErrorClassifier.ErrorCategory.RECOVERABLE : classified;

        iteration.setStatus("FAILED");
        iteration.setCompletedAt(Instant.now());
        buildIterationRepository.save(iteration);

        // Count existing errors for this iteration to determine retry count
        int retryCount = buildErrorRepository.findByIterationId(iteration.getId()).size();

        BuildError error = new BuildError();
        error.setSessionId(sessionId);
        error.setIterationId(iteration.getId());
        error.setCategory(classified.name());
        error.setSeverity("ERROR");
        error.setMessage(message != null ? message : "Unknown error");
        if (e != null) {
            error.setStackTrace(getStackTraceString(e));
        }
        error.setRetryCount(retryCount);
        buildErrorRepository.save(error);

        // Hard cap on auto-fix attempts regardless of remaining budget. Count
        // only the trailing run of AUTO_RETRY iterations — i.e. since the most
        // recent MANUAL trigger (INITIAL / ADMIN_*) — so a fresh admin retrigger
        // grants a fresh set of auto-fix attempts instead of being permanently
        // blocked by historical retries.
        java.util.List<BuildIteration> sessionIters =
                buildIterationRepository.findBySessionIdOrderByIterationNumberAsc(sessionId);
        long autoRetries = 0;
        for (int i = sessionIters.size() - 1; i >= 0; i--) {
            if ("AUTO_RETRY".equals(sessionIters.get(i).getTrigger())) {
                autoRetries++;
            } else {
                break;
            }
        }

        // Check if we should retry — feeding the failed source + the failure
        // detail into a targeted repair instead of a blind regeneration.
        if (autoRetries < MAX_AUTO_RETRIES
                && retryPolicy.shouldRetry(sessionId, retryCategory, retryCount)) {
            ImplementerAgent.RepairContext repair =
                    (previousFiles != null && !previousFiles.isEmpty())
                            ? new ImplementerAgent.RepairContext(previousFiles,
                                    category + " failure:\n" + message)
                            : null;
            retryBuild(sessionId, message, repair);
            return;
        }

        buildSessionService.updateStatus(sessionId, BuildStatus.FAILED);
        buildProgressService.notifyStatusChange(sessionId, BuildStatus.FAILED);
        buildProgressService.notifyError(sessionId, message);
        emailNotificationService.notifyBuildFailed(sessionId, category);
    }

    private void retryBuild(UUID sessionId, String errorMessage, ImplementerAgent.RepairContext repair) {
        // User-facing note only — NEVER leak the raw compiler/runtime/bot output
        // into the chat. The technical detail is preserved in build_errors and
        // fed to the implementer via the repair context, not shown to the user.
        chatMessageService.addMessage(sessionId, "system",
                "⚙️ A build issue came up — automatically retrying with a fix…", null, 0);

        // Create a new iteration for the retry
        int iterationNumber = buildIterationRepository
                .findBySessionIdOrderByIterationNumberAsc(sessionId).size() + 1;

        BuildIteration retryIteration = new BuildIteration();
        retryIteration.setSessionId(sessionId);
        retryIteration.setIterationNumber(iterationNumber);
        retryIteration.setStatus("RUNNING");
        retryIteration.setTrigger("AUTO_RETRY");
        retryIteration = buildIterationRepository.save(retryIteration);

        UUID retryIterationId = retryIteration.getId();

        Map<String, String> files = null;
        try {
            // Re-run implementation — as a TARGETED repair when we have the
            // previous files + failure detail, otherwise a fresh generation.
            buildSessionService.updatePhase(sessionId, BuildPhase.IMPLEMENTATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.IMPLEMENTATION);

            ImplementationResult result = implementerAgent.implement(sessionId, repair);
            files = result.files();
            tokenBudgetService.consumeTokens(sessionId, "implementation", result.tokensUsed());

            // Re-run compilation
            buildSessionService.updatePhase(sessionId, BuildPhase.COMPILATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.COMPILATION);

            byte[] jarBytes = compileInContainer(sessionId, retryIterationId, files);

            // Phases 3-6: security scan, runtime smoke test, functional test, deliver.
            finalizeBuild(sessionId, retryIteration, files, jarBytes);

        } catch (CompilationException ce) {
            log.error("Retry compilation failed for session {}: {}", sessionId, ce.getMessage());
            handleBuildError(retryIteration, sessionId, "COMPILATION", ce.getMessage(), ce, files);

        } catch (RuntimeTestException re) {
            log.error("Retry runtime test failed for session {}: {}", sessionId, re.getMessage());
            handleBuildError(retryIteration, sessionId, "RUNTIME", re.getMessage(), re, files);

        } catch (FunctionalTestException fe) {
            log.error("Retry functional test failed for session {}: {}", sessionId, fe.getMessage());
            handleBuildError(retryIteration, sessionId, "FUNCTIONAL", fe.getMessage(), fe, files);

        } catch (SecurityViolationException se) {
            log.error("Retry security scan failed for session {}: {}", sessionId, se.getMessage());
            handleBuildError(retryIteration, sessionId, "SECURITY", se.getMessage(), se, files);

        } catch (Exception ex) {
            log.error("Retry build failed for session {}: {}", sessionId, ex.getMessage(), ex);
            handleBuildError(retryIteration, sessionId, "GENERAL", ex.getMessage(), ex, files);
        }
    }

    /**
     * Reads the bytes of the first entry of a single-file tar (as Docker's
     * copy-from-container returns for one file). Only the first entry is read,
     * so commons-compress never invokes its multi-entry skip path.
     */
    static byte[] firstTarEntryBytes(byte[] tar) {
        try (TarArchiveInputStream tis =
                     new TarArchiveInputStream(new ByteArrayInputStream(tar))) {
            if (tis.getNextEntry() == null) {
                return null;
            }
            return tis.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read jar tar", e);
        }
    }

    byte[] createTarArchive(Map<String, String> files) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {

            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            for (Map.Entry<String, String> entry : files.entrySet()) {
                byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getKey());
                tarEntry.setSize(content.length);
                tar.putArchiveEntry(tarEntry);
                tar.write(content);
                tar.closeArchiveEntry();
            }

            tar.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to create tar archive", e);
        }
    }

    byte[] createSourceZip(Map<String, String> files) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Map.Entry<String, String> entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to create source zip", e);
        }
    }

    String extractPluginYml(Map<String, String> files) {
        return files.entrySet().stream()
                .filter(e -> e.getKey().endsWith("plugin.yml"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String concatenateSource(Map<String, String> files) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (entry.getKey().endsWith(".java")) {
                sb.append("// File: ").append(entry.getKey()).append("\n");
                sb.append(entry.getValue()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String getStackTraceString(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
            if (sb.length() > 4000) {
                sb.append("\t... truncated");
                break;
            }
        }
        return sb.toString();
    }
}
