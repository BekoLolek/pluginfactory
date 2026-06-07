package com.bekololek.pluginfactory.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DockerService {

    private DockerClient dockerClient;
    private final String dockerHost;
    private final String buildImage;
    private final String testImage;
    private final ContainerSecurityConfig securityConfig;

    public DockerService(@Value("${docker.host}") String dockerHost,
                         @Value("${docker.build-image}") String buildImage,
                         @Value("${docker.test-image}") String testImage,
                         ContainerSecurityConfig securityConfig) {
        this.dockerHost = dockerHost;
        this.buildImage = buildImage;
        this.testImage = testImage;
        this.securityConfig = securityConfig;
    }

    @PostConstruct
    void init() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .build();
            DockerClient client = DockerClientImpl.getInstance(config, httpClient);
            // Verify connectivity by pinging Docker daemon
            client.pingCmd().exec();
            this.dockerClient = client;
            log.info("Docker client initialized with host: {}", dockerHost);
        } catch (Exception e) {
            log.warn("Docker client initialization failed: {}. Container features will be unavailable.",
                    e.getMessage());
        } catch (Error e) {
            log.warn("Docker client initialization failed: {}. Container features will be unavailable.",
                    e.getMessage());
        }
    }

    public enum ContainerType { BUILD, TEST }

    public boolean isAvailable() {
        return dockerClient != null;
    }

    public String createContainer(ContainerType type, Map<String, String> env) {
        requireClient();
        String image = type == ContainerType.BUILD ? buildImage : testImage;

        String[] envArray = env.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);

        // Label every factory-spawned container so orphans from a previous
        // API instance (the in-memory pool is lost on restart) can be found
        // and reaped without guessing from image ancestry. MANAGED_LABEL is
        // the marker; the role is informational.
        Map<String, String> labels = Map.of(
                MANAGED_LABEL, "true",
                "pluginfactory.role", type.name().toLowerCase());

        CreateContainerResponse response = withDockerRetry("createContainer", () ->
                dockerClient.createContainerCmd(image)
                        .withEnv(envArray)
                        .withLabels(labels)
                        .withHostConfig(securityConfig.getSecurityConstraints(type))
                        .exec());

        log.info("Created {} container: {}", type, response.getId());
        return response.getId();
    }

    /** Marker label applied to every container the factory creates. */
    public static final String MANAGED_LABEL = "pluginfactory.managed";

    /**
     * IDs of all factory containers currently known to Docker (running or
     * exited), regardless of which API instance created them. Used to reap
     * orphans left behind by a previous instance's lost pool.
     *
     * <p>Matches on both the {@link #MANAGED_LABEL} (containers created since
     * labelling was introduced) and the factory build/test image ancestry
     * (legacy containers created before the label existed), so a single deploy
     * clears the entire pre-existing orphan backlog.
     */
    public java.util.List<String> listManagedContainerIds() {
        requireClient();
        return withDockerRetry("listManaged", () -> {
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of(MANAGED_LABEL, "true"))
                    .exec()
                    .forEach(c -> ids.add(c.getId()));
            dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withAncestorFilter(java.util.List.of(buildImage, testImage))
                    .exec()
                    .forEach(c -> ids.add(c.getId()));
            return java.util.List.copyOf(ids);
        });
    }

    /**
     * The API talks to Docker through a docker-socket-proxy (haproxy) which
     * closes idle keep-alive connections. A request that grabs a stale pooled
     * connection fails with {@code NoHttpResponseException: docker-proxy:2375
     * failed to respond} — a transient that has been failing otherwise-good
     * builds. Retrying on a fresh connection succeeds, so wrap Docker calls in
     * a small bounded retry that only re-attempts on stale-connection errors.
     */
    private <T> T withDockerRetry(String op, java.util.function.Supplier<T> action) {
        int attempt = 0;
        while (true) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                attempt++;
                if (attempt >= 3 || !isStaleConnection(e)) {
                    throw e;
                }
                log.warn("Docker op '{}' hit a stale proxy connection (attempt {}/3), retrying: {}",
                        op, attempt, e.getMessage());
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private boolean isStaleConnection(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.apache.hc.core5.http.NoHttpResponseException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null && (m.contains("failed to respond")
                    || m.contains("Connection reset")
                    || m.contains("Broken pipe")
                    || m.contains("Connection is closed"))) {
                return true;
            }
        }
        return false;
    }

    public void startContainer(String containerId) {
        requireClient();
        dockerClient.startContainerCmd(containerId).exec();
        log.debug("Started container: {}", containerId);
    }

    public void stopContainer(String containerId) {
        requireClient();
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.debug("Stopped container: {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
        }
    }

    public void removeContainer(String containerId) {
        requireClient();
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.debug("Removed container: {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    public ExecResult executeCommand(String containerId, String... command) {
        // Retry on stale-proxy-connection failures (NoHttpResponseException).
        // Our commands (mvn clean package, server boot, rm -rf) are safe to
        // re-run, and the transient almost always strikes at exec-create time
        // before the command runs.
        return withDockerRetry("executeCommand", () -> executeCommandOnce(containerId, command));
    }

    private ExecResult executeCommandOnce(String containerId, String... command) {
        requireClient();

        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            dockerClient.execStartCmd(execCreate.getId())
                    .exec(new ResultCallback<Frame>() {
                        @Override
                        public void onStart(Closeable closeable) {
                            // no-op
                        }

                        @Override
                        public void onNext(Frame frame) {
                            String payload = new String(frame.getPayload());
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                stdout.append(payload);
                            } else if (frame.getStreamType() == StreamType.STDERR) {
                                stderr.append(payload);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Error executing command in container {}: {}",
                                    containerId, throwable.getMessage());
                            latch.countDown();
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }

                        @Override
                        public void close() throws IOException {
                            latch.countDown();
                        }
                    });

            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (!completed) {
                log.warn("Command execution timed out in container {}", containerId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Command execution interrupted in container {}", containerId);
        }

        InspectExecResponse inspectExec = dockerClient.inspectExecCmd(execCreate.getId()).exec();
        Long exitCodeLong = inspectExec.getExitCodeLong();
        int exitCode = exitCodeLong != null ? exitCodeLong.intValue() : -1;

        return new ExecResult(exitCode, stdout.toString(), stderr.toString());
    }

    public void copyToContainer(String containerId, byte[] tarContent, String destPath) {
        requireClient();
        withDockerRetry("copyToContainer", () -> {
            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new java.io.ByteArrayInputStream(tarContent))
                    .withRemotePath(destPath)
                    .exec();
            return null;
        });
        log.debug("Copied archive to container {} at {}", containerId, destPath);
    }

    public byte[] copyFromContainer(String containerId, String sourcePath) {
        requireClient();
        return withDockerRetry("copyFromContainer", () -> {
            try (InputStream is = dockerClient.copyArchiveFromContainerCmd(containerId, sourcePath).exec()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                log.debug("Copied archive from container {} at {}", containerId, sourcePath);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy from container " + containerId, e);
            }
        });
    }

    private void requireClient() {
        if (dockerClient == null) {
            throw new IllegalStateException("Docker client is not available");
        }
    }
}
