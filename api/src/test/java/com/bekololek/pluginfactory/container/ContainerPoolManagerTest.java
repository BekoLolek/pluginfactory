package com.bekololek.pluginfactory.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContainerPoolManagerTest {

    @Mock
    private DockerService dockerService;

    @InjectMocks
    private ContainerPoolManager poolManager;

    @Test
    void claimFromWarmPool() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("container-1");

        // Manually add a container to the pool by creating and releasing
        when(dockerService.executeCommandAsRoot(eq("container-1"), any(String[].class)))
                .thenReturn(new ExecResult(0, "", ""));

        String containerId = poolManager.claimContainer(DockerService.ContainerType.BUILD);
        assertThat(containerId).isEqualTo("container-1");

        // Release it back
        poolManager.releaseContainer(containerId, DockerService.ContainerType.BUILD);

        // Now claim again - should get same container from pool
        String reused = poolManager.claimContainer(DockerService.ContainerType.BUILD);
        assertThat(reused).isEqualTo("container-1");
    }

    @Test
    void claimWhenPoolEmpty_createsNew() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("new-container");

        String containerId = poolManager.claimContainer(DockerService.ContainerType.BUILD);

        assertThat(containerId).isEqualTo("new-container");
        verify(dockerService).createContainer(DockerService.ContainerType.BUILD, Map.of());
        verify(dockerService).startContainer("new-container");
    }

    @Test
    void releaseBackToPool() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.TEST), any()))
                .thenReturn("test-container");
        when(dockerService.executeCommandAsRoot(eq("test-container"), any(String[].class)))
                .thenReturn(new ExecResult(0, "", ""));

        String containerId = poolManager.claimContainer(DockerService.ContainerType.TEST);
        poolManager.releaseContainer(containerId, DockerService.ContainerType.TEST);

        Map<String, Integer> status = poolManager.getPoolStatus();
        assertThat(status.get("warmTest")).isEqualTo(1);
    }

    @Test
    void getPoolStatus_emptyInitially() {
        Map<String, Integer> status = poolManager.getPoolStatus();

        assertThat(status.get("warmBuild")).isZero();
        assertThat(status.get("warmTest")).isZero();
    }

    @Test
    void initializePool_skipsWhenDockerUnavailable() {
        when(dockerService.isAvailable()).thenReturn(false);

        poolManager.initializePool();

        verify(dockerService, never()).createContainer(any(), any());
    }

    @Test
    void reapOrphans_removesUnownedManagedContainers() {
        // Two managed containers exist in Docker; this instance owns neither
        // (its pool is empty), so both are orphans from a prior instance.
        when(dockerService.listManagedContainerIds())
                .thenReturn(java.util.List.of("orphan-a", "orphan-b"));

        poolManager.reapOrphans();

        verify(dockerService).removeContainer("orphan-a");
        verify(dockerService).removeContainer("orphan-b");
    }

    @Test
    void reapOrphans_keepsContainersThisInstanceOwns() {
        // Claim a container so this instance owns it...
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("mine");
        String mine = poolManager.claimContainer(DockerService.ContainerType.BUILD);

        // ...and have Docker report it plus a real orphan as managed.
        when(dockerService.listManagedContainerIds())
                .thenReturn(java.util.List.of(mine, "orphan"));

        poolManager.reapOrphans();

        verify(dockerService).removeContainer("orphan");
        verify(dockerService, never()).removeContainer("mine");
    }

    @Test
    void releaseContainer_removesOnFailure() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("failing-container");
        when(dockerService.executeCommandAsRoot(eq("failing-container"), any(String[].class)))
                .thenThrow(new RuntimeException("container stopped"));

        String containerId = poolManager.claimContainer(DockerService.ContainerType.BUILD);
        poolManager.releaseContainer(containerId, DockerService.ContainerType.BUILD);

        verify(dockerService).removeContainer("failing-container");
        assertThat(poolManager.getPoolStatus().get("warmBuild")).isZero();
    }

    @Test
    void claimCleanBuildContainer_returnsContainerWithEmptyWorkspace() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("clean-1");
        // Empty stdout from the wipe+list = verified clean.
        when(dockerService.executeCommandAsRoot(eq("clean-1"), any(String[].class)))
                .thenReturn(new ExecResult(0, "", ""));

        String id = poolManager.claimCleanBuildContainer();

        assertThat(id).isEqualTo("clean-1");
    }

    @Test
    void claimCleanBuildContainer_discardsDirtyContainerAndRetries() {
        when(dockerService.createContainer(eq(DockerService.ContainerType.BUILD), any()))
                .thenReturn("dirty-1", "clean-2");
        // First container still lists a leftover file (dirty); second is empty.
        when(dockerService.executeCommandAsRoot(eq("dirty-1"), any(String[].class)))
                .thenReturn(new ExecResult(0, "/plugin-workspace/src/Foo.java", ""));
        when(dockerService.executeCommandAsRoot(eq("clean-2"), any(String[].class)))
                .thenReturn(new ExecResult(0, "", ""));

        String id = poolManager.claimCleanBuildContainer();

        assertThat(id).isEqualTo("clean-2");
        verify(dockerService).removeContainer("dirty-1");
    }
}
