package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.agent.ImplementerAgent;
import com.bekololek.pluginfactory.agent.dto.ImplementationResult;
import com.bekololek.pluginfactory.common.config.AsyncConfig;
import com.bekololek.pluginfactory.common.logging.MdcRequestFilter;
import org.slf4j.MDC;
import com.bekololek.pluginfactory.container.ContainerPoolManager;
import com.bekololek.pluginfactory.container.ContainerSession;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.container.DockerService;
import com.bekololek.pluginfactory.container.ExecResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

        try {
            // Phase 1: IMPLEMENTATION
            buildSessionService.updatePhase(sessionId, BuildPhase.IMPLEMENTATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.IMPLEMENTATION);

            ImplementationResult result = implementerAgent.implement(sessionId);
            Map<String, String> files = result.files();
            tokenBudgetService.consumeTokens(sessionId, "implementation", result.tokensUsed());

            // Phase 2: COMPILATION
            buildSessionService.updatePhase(sessionId, BuildPhase.COMPILATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.COMPILATION);

            byte[] jarBytes = compileInContainer(sessionId, iterationId, files);

            // Phase 3: SECURITY_SCAN
            buildSessionService.updatePhase(sessionId, BuildPhase.SECURITY_SCAN);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.SECURITY_SCAN);

            String allSource = concatenateSource(files);
            SecurityScanResult scanResult = securityScanService.scanSource(allSource);
            if (!scanResult.passed()) {
                throw new SecurityViolationException(scanResult.violations());
            }

            // Create source ZIP
            byte[] sourceZipBytes = createSourceZip(files);

            // Extract plugin.yml
            String pluginYml = extractPluginYml(files);

            // Phase 4: DELIVERING
            buildSessionService.updatePhase(sessionId, BuildPhase.DELIVERING);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.DELIVERING);

            artifactService.storeArtifact(sessionId, iterationId, jarBytes, sourceZipBytes,
                    pluginYml, scanResult.passed());

            // Complete
            iteration.setStatus("COMPLETED");
            iteration.setCompletedAt(Instant.now());
            buildIterationRepository.save(iteration);

            buildSessionService.updateStatus(sessionId, BuildStatus.COMPLETED);
            buildSessionService.updatePhase(sessionId, BuildPhase.IDLE);
            buildProgressService.notifyStatusChange(sessionId, BuildStatus.COMPLETED);

            log.info("Build completed successfully for session {}", sessionId);

        } catch (CompilationException e) {
            log.error("Compilation failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "COMPILATION", e.getMessage(), e);

        } catch (SecurityViolationException e) {
            log.error("Security scan failed for session {}: {}", sessionId, e.getMessage());
            handleBuildError(iteration, sessionId, "SECURITY", e.getMessage(), e);

        } catch (Exception e) {
            log.error("Build failed for session {}: {}", sessionId, e.getMessage(), e);
            handleBuildError(iteration, sessionId, "GENERAL", e.getMessage(), e);
        }
    }

    private byte[] compileInContainer(UUID sessionId, UUID iterationId,
                                       Map<String, String> files) {
        String containerId = containerPoolManager.claimContainer(DockerService.ContainerType.BUILD);

        // Track container session
        ContainerSession containerSession = new ContainerSession();
        containerSession.setIterationId(iterationId);
        containerSession.setContainerId(containerId);
        containerSession.setContainerType("BUILD");
        containerSession.setMemoryMb(3072);
        containerSession.setCpuMillicores(1000);
        containerSessionRepository.save(containerSession);

        try {
            // Create tar archive and copy to container
            byte[] tarArchive = createTarArchive(files);
            dockerService.copyToContainer(containerId, tarArchive, "/plugin-workspace");

            // Run Maven build — cap JVM heap to leave room for OS/native overhead
            ExecResult buildResult = dockerService.executeCommand(containerId,
                    "sh", "-c", "cd /plugin-workspace && MAVEN_OPTS='-Xmx1536m -Xms256m' mvn clean package -q -DskipTests");

            if (buildResult.exitCode() != 0) {
                throw new CompilationException(
                        "Maven build failed (exit code " + buildResult.exitCode() + "): " +
                                buildResult.stderr() + buildResult.stdout());
            }

            // Extract the JAR
            byte[] jarTar = dockerService.copyFromContainer(containerId,
                    "/plugin-workspace/target/");

            return jarTar;

        } finally {
            // Release container back to pool
            containerSession.setReleasedAt(Instant.now());
            containerSessionRepository.save(containerSession);
            containerPoolManager.releaseContainer(containerId, DockerService.ContainerType.BUILD);
        }
    }

    private void handleBuildError(BuildIteration iteration, UUID sessionId,
                                   String category, String message, Exception e) {
        ErrorClassifier.ErrorCategory classified = errorClassifier.classify(message);

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

        // Check if we should retry
        if (retryPolicy.shouldRetry(sessionId, classified, retryCount)) {
            retryBuild(sessionId, message);
            return;
        }

        buildSessionService.updateStatus(sessionId, BuildStatus.FAILED);
        buildProgressService.notifyStatusChange(sessionId, BuildStatus.FAILED);
        buildProgressService.notifyError(sessionId, message);
    }

    private void retryBuild(UUID sessionId, String errorMessage) {
        // Add error context as a chat message so the AI can see what went wrong
        chatMessageService.addMessage(sessionId, "system",
                "Build failed with error: " + errorMessage + "\nPlease fix the issue and try again.",
                null, 0);

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

        try {
            // Re-run implementation
            buildSessionService.updatePhase(sessionId, BuildPhase.IMPLEMENTATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.IMPLEMENTATION);

            ImplementationResult result = implementerAgent.implement(sessionId);
            Map<String, String> files = result.files();
            tokenBudgetService.consumeTokens(sessionId, "implementation", result.tokensUsed());

            // Re-run compilation
            buildSessionService.updatePhase(sessionId, BuildPhase.COMPILATION);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.COMPILATION);

            byte[] jarBytes = compileInContainer(sessionId, retryIterationId, files);

            // Re-run security scan
            buildSessionService.updatePhase(sessionId, BuildPhase.SECURITY_SCAN);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.SECURITY_SCAN);

            String allSource = concatenateSource(files);
            SecurityScanResult scanResult = securityScanService.scanSource(allSource);
            if (!scanResult.passed()) {
                throw new SecurityViolationException(scanResult.violations());
            }

            byte[] sourceZipBytes = createSourceZip(files);
            String pluginYml = extractPluginYml(files);

            buildSessionService.updatePhase(sessionId, BuildPhase.DELIVERING);
            buildProgressService.notifyPhaseChange(sessionId, BuildPhase.DELIVERING);

            artifactService.storeArtifact(sessionId, retryIterationId, jarBytes, sourceZipBytes,
                    pluginYml, scanResult.passed());

            retryIteration.setStatus("COMPLETED");
            retryIteration.setCompletedAt(Instant.now());
            buildIterationRepository.save(retryIteration);

            buildSessionService.updateStatus(sessionId, BuildStatus.COMPLETED);
            buildSessionService.updatePhase(sessionId, BuildPhase.IDLE);
            buildProgressService.notifyStatusChange(sessionId, BuildStatus.COMPLETED);

            log.info("Retry build completed successfully for session {}", sessionId);

        } catch (CompilationException ce) {
            log.error("Retry compilation failed for session {}: {}", sessionId, ce.getMessage());
            handleBuildError(retryIteration, sessionId, "COMPILATION", ce.getMessage(), ce);

        } catch (SecurityViolationException se) {
            log.error("Retry security scan failed for session {}: {}", sessionId, se.getMessage());
            handleBuildError(retryIteration, sessionId, "SECURITY", se.getMessage(), se);

        } catch (Exception ex) {
            log.error("Retry build failed for session {}: {}", sessionId, ex.getMessage(), ex);
            handleBuildError(retryIteration, sessionId, "GENERAL", ex.getMessage(), ex);
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
