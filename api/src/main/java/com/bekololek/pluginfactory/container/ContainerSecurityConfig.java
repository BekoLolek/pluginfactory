package com.bekololek.pluginfactory.container;

import com.github.dockerjava.api.model.HostConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContainerSecurityConfig {

    public HostConfig getSecurityConstraints(DockerService.ContainerType type) {
        long memoryBytes = type == DockerService.ContainerType.BUILD
                ? 2L * 1024 * 1024 * 1024 : 4L * 1024 * 1024 * 1024;

        return HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withCpuQuota(200_000L)
                .withCpuPeriod(100_000L)
                .withPidsLimit(256L)
                .withNetworkMode(type == DockerService.ContainerType.TEST ? "none" : "bridge")
                .withSecurityOpts(List.of("no-new-privileges"));
    }
}
