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

    public void releaseContainer(String containerId, DockerService.ContainerType type) {
        try {
            dockerService.executeCommand(containerId, "sh", "-c", "rm -rf /plugin-workspace/*");
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
                dockerService.stopContainer(id);
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
