package com.bekololek.pluginfactory.container;

import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSecurityConfigTest {

    private final ContainerSecurityConfig securityConfig = new ContainerSecurityConfig();

    @Test
    void buildConstraints_memoryIs3GB() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.BUILD);

        assertThat(config.getMemory()).isEqualTo(3L * 1024 * 1024 * 1024);
        assertThat(config.getMemorySwap()).isEqualTo(3L * 1024 * 1024 * 1024);
    }

    @Test
    void testConstraints_memoryIs4GB() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.TEST);

        assertThat(config.getMemory()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(config.getMemorySwap()).isEqualTo(4L * 1024 * 1024 * 1024);
    }

    @Test
    void buildConstraints_cpuQuotaAndPeriod() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.BUILD);

        assertThat(config.getCpuQuota()).isEqualTo(200_000L);
        assertThat(config.getCpuPeriod()).isEqualTo(100_000L);
    }

    @Test
    void testConstraints_cpuQuotaAndPeriod() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.TEST);

        assertThat(config.getCpuQuota()).isEqualTo(200_000L);
        assertThat(config.getCpuPeriod()).isEqualTo(100_000L);
    }

    @Test
    void buildConstraints_networkIsBridge() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.BUILD);

        assertThat(config.getNetworkMode()).isEqualTo("bridge");
    }

    @Test
    void testConstraints_networkIsNone() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.TEST);

        assertThat(config.getNetworkMode()).isEqualTo("none");
    }

    @Test
    void buildConstraints_pidsLimit() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.BUILD);

        assertThat(config.getPidsLimit()).isEqualTo(256L);
    }

    @Test
    void buildConstraints_securityOpts() {
        HostConfig config = securityConfig.getSecurityConstraints(DockerService.ContainerType.BUILD);

        assertThat(config.getSecurityOpts()).containsExactly("no-new-privileges");
    }
}
