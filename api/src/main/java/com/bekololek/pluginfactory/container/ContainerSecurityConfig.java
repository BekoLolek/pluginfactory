package com.bekololek.pluginfactory.container;

import com.github.dockerjava.api.model.HostConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContainerSecurityConfig {

    public HostConfig getSecurityConstraints(DockerService.ContainerType type) {
        long memoryBytes = type == DockerService.ContainerType.BUILD
                ? 3L * 1024 * 1024 * 1024 : 4L * 1024 * 1024 * 1024;
        // memorySwap = memory + swap. For BUILD containers, allow up to 2GB
        // of swap so Maven dependency resolution doesn't OOM-kill. For TEST
        // containers, keep swap disabled (memorySwap == memory).
        long memorySwapBytes = type == DockerService.ContainerType.BUILD
                ? memoryBytes + 2L * 1024 * 1024 * 1024 : memoryBytes;

        return HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withMemorySwap(memorySwapBytes)
                .withCpuQuota(200_000L)
                .withCpuPeriod(100_000L)
                .withPidsLimit(256L)
                .withNetworkMode(type == DockerService.ContainerType.TEST ? "none" : "bridge")
                .withSecurityOpts(List.of("no-new-privileges"));
    }
}
