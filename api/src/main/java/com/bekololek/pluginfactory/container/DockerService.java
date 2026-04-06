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

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withEnv(envArray)
                .withHostConfig(securityConfig.getSecurityConstraints(type))
                .exec();

        log.info("Created {} container: {}", type, response.getId());
        return response.getId();
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
        dockerClient.copyArchiveToContainerCmd(containerId)
                .withTarInputStream(new java.io.ByteArrayInputStream(tarContent))
                .withRemotePath(destPath)
                .exec();
        log.debug("Copied archive to container {} at {}", containerId, destPath);
    }

    public byte[] copyFromContainer(String containerId, String sourcePath) {
        requireClient();
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
    }

    private void requireClient() {
        if (dockerClient == null) {
            throw new IllegalStateException("Docker client is not available");
        }
    }
}
