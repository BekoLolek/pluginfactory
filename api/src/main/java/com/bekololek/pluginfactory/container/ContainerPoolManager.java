package com.bekololek.pluginfactory.container;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContainerPoolManager {

    private final DockerService dockerService;

    private final ConcurrentLinkedQueue<String> buildPool = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> testPool = new ConcurrentLinkedQueue<>();

    /**
     * Every container id this API instance created and still owns (warm in a
     * pool OR claimed and in-flight). A managed container that is NOT in this
     * set belongs to a previous, now-dead instance whose in-memory pool was
     * lost on restart — i.e. an orphan we must reap. This protects in-flight
     * claimed containers (which are not in any pool queue) from being killed.
     */
    private final Set<String> owned = ConcurrentHashMap.newKeySet();

    @EventListener(ApplicationReadyEvent.class)
    public void initializePool() {
        if (!dockerService.isAvailable()) {
            log.info("Docker not available, skipping container pool initialization");
            return;
        }

        // Reap any factory containers left running by a previous instance
        // before we build a fresh pool. On startup `owned` is empty, so this
        // removes the entire orphaned set from prior restarts/deploys.
        reapOrphans();

        try {
            log.info("Initializing container pools...");
            for (int i = 0; i < 2; i++) {
                String buildId = createAndStartContainer(DockerService.ContainerType.BUILD);
                buildPool.offer(buildId);
            }
            for (int i = 0; i < 1; i++) {
                String testId = createAndStartContainer(DockerService.ContainerType.TEST);
                testPool.offer(testId);
            }
            log.info("Container pools initialized: build={}, test={}", buildPool.size(), testPool.size());
        } catch (Exception e) {
            log.warn("Failed to initialize container pools: {}", e.getMessage());
        }
    }

    public String claimContainer(DockerService.ContainerType type) {
        ConcurrentLinkedQueue<String> pool = type == DockerService.ContainerType.BUILD ? buildPool : testPool;
        String containerId = pool.poll();

        if (containerId != null) {
            log.debug("Claimed warm {} container: {}", type, containerId);
            return containerId;
        }

        log.debug("No warm {} container available, creating new one", type);
        return createAndStartContainer(type);
    }

    /**
     * Claim a BUILD container that is <em>guaranteed</em> to have an empty
     * {@code /plugin-workspace}. Pooled containers are reused across sessions,
     * and an unreliable wipe was leaving one build's generated sources behind
     * to compile into the next (cross-session contamination). Here we wipe as
     * root (the copied-in files are root-owned) and then verify the workspace
     * is actually empty; if a container can't be verified clean — stale files
     * survived, or the docker-proxy dropped the cleanup exec — we discard it
     * and try another rather than ever compiling in a dirty workspace.
     */
    public String claimCleanBuildContainer() {
        DockerService.ContainerType type = DockerService.ContainerType.BUILD;
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            String id = claimContainer(type);
            if (workspaceVerifiedClean(id)) {
                return id;
            }
            log.warn("Build container {} workspace not verifiably clean (attempt {}/3); discarding", id, attempt);
            owned.remove(id);
            dockerService.removeContainer(id);
            last = new IllegalStateException("Build container workspace could not be cleaned");
        }
        throw last != null ? last
                : new IllegalStateException("Could not obtain a clean build container");
    }

    private boolean workspaceVerifiedClean(String id) {
        try {
            // Delete contents as root (copied-in files are root-owned), keeping
            // the workspace dir itself, then list any survivors. -mindepth 1
            // keeps /plugin-workspace; an empty listing means a clean slate.
            ExecResult r = dockerService.executeCommandAsRoot(id, "sh", "-c",
                    "find /plugin-workspace -mindepth 1 -delete; find /plugin-workspace -mindepth 1");
            return r.exitCode() == 0 && r.stdout().trim().isEmpty();
        } catch (Exception e) {
            log.warn("Workspace verification failed for build container {}: {}", id, e.getMessage());
            return false;
        }
    }

    public void releaseContainer(String containerId, DockerService.ContainerType type) {
        try {
            // Wipe as root — copied-in files are root-owned, so a builder-user
            // rm would leave them behind. Clear both the build workspace and the
            // test server's plugins/work dirs so no container re-enters the pool
            // carrying another session's artifacts.
            dockerService.executeCommandAsRoot(containerId, "sh", "-c",
                    "find /plugin-workspace -mindepth 1 -delete 2>/dev/null; "
                    + "rm -rf /server/plugins/* /server/logs/* /work/* 2>/dev/null; true");
            ConcurrentLinkedQueue<String> pool = type == DockerService.ContainerType.BUILD ? buildPool : testPool;
            pool.offer(containerId);
            log.debug("Released {} container back to pool: {}", type, containerId);
        } catch (Exception e) {
            log.warn("Failed to release container {}, removing it: {}", containerId, e.getMessage());
            owned.remove(containerId);
            dockerService.removeContainer(containerId);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupStaleContainers() {
        if (!dockerService.isAvailable()) {
            return;
        }

        cleanPool(buildPool, DockerService.ContainerType.BUILD, 3);
        cleanPool(testPool, DockerService.ContainerType.TEST, 2);
        // Also sweep orphans from any prior instance that may still be running
        // (e.g. a redeploy that happened while this instance was up).
        reapOrphans();
    }

    /**
     * Force-remove every factory-managed container Docker still knows about
     * that this instance does not own. Safe to call repeatedly: containers in
     * our pools or claimed-and-in-flight are in {@link #owned} and skipped.
     */
    void reapOrphans() {
        try {
            List<String> managed = dockerService.listManagedContainerIds();
            int reaped = 0;
            for (String id : managed) {
                if (owned.contains(id)) {
                    continue;
                }
                log.info("Reaping orphaned factory container {} (not owned by this instance)", id);
                // Force-remove directly: an orphan needs no graceful shutdown,
                // and skipping the 10s stop timeout keeps startup snappy even
                // when reaping a large backlog.
                dockerService.removeContainer(id);
                reaped++;
            }
            if (reaped > 0) {
                log.info("Reaped {} orphaned factory container(s)", reaped);
            }
        } catch (Exception e) {
            log.warn("Orphan reap sweep failed: {}", e.getMessage());
        }
    }

    public Map<String, Integer> getPoolStatus() {
        return Map.of(
                "warmBuild", buildPool.size(),
                "warmTest", testPool.size()
        );
    }

    private String createAndStartContainer(DockerService.ContainerType type) {
        String containerId = dockerService.createContainer(type, Map.of());
        owned.add(containerId);
        dockerService.startContainer(containerId);
        return containerId;
    }

    private void cleanPool(ConcurrentLinkedQueue<String> pool, DockerService.ContainerType type, int maxSize) {
        while (pool.size() > maxSize) {
            String containerId = pool.poll();
            if (containerId != null) {
                log.info("Cleaning up excess {} container: {}", type, containerId);
                owned.remove(containerId);
                dockerService.stopContainer(containerId);
                dockerService.removeContainer(containerId);
            }
        }
    }
}
