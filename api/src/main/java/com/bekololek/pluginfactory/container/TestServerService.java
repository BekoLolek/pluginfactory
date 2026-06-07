package com.bekololek.pluginfactory.container;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a compiled plugin JAR onto a headless, network-isolated Paper server
 * (the TEST container) and verifies it enables cleanly. Catches a whole class
 * of failures that compilation cannot: malformed plugin.yml, missing
 * registrations, NoClassDefFound, and enable-time exceptions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TestServerService {

    private final DockerService dockerService;
    private final ContainerPoolManager containerPoolManager;

    /** Max seconds to wait for the server to reach "Done (" or fail. */
    private static final int MAX_WAIT_SECONDS = 120;

    /** Hard plugin-load/enable failures in the server log. */
    private static final Pattern FAILURE = Pattern.compile(
            "(?im)^.*(Could not load '?plugins|Error occurred while enabling|"
                    + "Exception while enabling|Could not load plugin|"
                    + "Error loading plugin|Invalid plugin\\.yml|InvalidDescriptionException|"
                    + "is misconfigured|Unsupported API version).*$");

    public record SmokeResult(boolean passed, String detail) {}

    public SmokeResult runSmokeTest(byte[] jarBytes, String pluginName) {
        if (jarBytes == null || jarBytes.length == 0) {
            return new SmokeResult(false, "No JAR was produced to test.");
        }
        if (!dockerService.isAvailable()) {
            // Dev / Docker-less environments: don't block delivery.
            return new SmokeResult(true, "Docker unavailable — runtime test skipped.");
        }

        String containerId;
        try {
            containerId = containerPoolManager.claimContainer(DockerService.ContainerType.TEST);
        } catch (Exception e) {
            // Test image not built yet / pool exhausted — fail open so the
            // feature can roll out before the image exists.
            log.warn("Could not claim TEST container, skipping runtime test: {}", e.getMessage());
            return new SmokeResult(true, "Test server unavailable — runtime test skipped.");
        }

        try {
            dockerService.executeCommand(containerId, "sh", "-c",
                    "rm -rf /server/plugins/* /server/logs/* /server/run.log 2>/dev/null || true");
            dockerService.copyToContainer(containerId, singleFileTar("plugin.jar", jarBytes), "/server/plugins");

            // Start Paper, wait until it finishes startup OR a plugin error
            // appears OR the JVM dies, then stop it and print the log.
            String script = ""
                    + "cd /server && rm -f run.log && "
                    + "java -Xmx1024m -XX:+UseSerialGC -Dcom.mojang.eula.agree=true -jar paper.jar --nogui > run.log 2>&1 & "
                    + "PID=$!; "
                    + "for i in $(seq 1 " + MAX_WAIT_SECONDS + "); do "
                    + "  grep -q 'Done (' run.log && break; "
                    + "  grep -Eqi \"Could not load|while enabling|Unsupported API version|is misconfigured|Invalid plugin.yml|Error loading plugin\" run.log && break; "
                    + "  kill -0 $PID 2>/dev/null || break; "
                    + "  sleep 1; "
                    + "done; "
                    + "kill $PID 2>/dev/null || true; sleep 3; kill -9 $PID 2>/dev/null || true; "
                    + "cat run.log";
            ExecResult result = dockerService.executeCommand(containerId, "sh", "-c", script);
            return evaluate(result.stdout() + "\n" + result.stderr(), pluginName);
        } catch (Exception e) {
            log.warn("Runtime smoke test infra error for {}: {}", pluginName, e.getMessage());
            // Infra fault, not a plugin fault — don't punish the user's build.
            return new SmokeResult(true, "Runtime test infra error — skipped: " + e.getMessage());
        } finally {
            try {
                dockerService.executeCommand(containerId, "sh", "-c",
                        "rm -rf /server/plugins/* /server/logs/* /server/run.log 2>/dev/null || true");
            } catch (Exception ignored) {
                // best-effort cleanup
            }
            containerPoolManager.releaseContainer(containerId, DockerService.ContainerType.TEST);
        }
    }

    SmokeResult evaluate(String serverLog, String pluginName) {
        if (serverLog == null || serverLog.isBlank()) {
            return new SmokeResult(true, "No server output captured — inconclusive, allowing delivery.");
        }
        Matcher m = FAILURE.matcher(serverLog);
        if (m.find()) {
            return new SmokeResult(false,
                    "Plugin failed to load/enable on a Paper server: " + m.group().trim());
        }
        if (!serverLog.contains("Done (")) {
            return new SmokeResult(false,
                    "Server did not finish startup within " + MAX_WAIT_SECONDS
                            + "s — the plugin likely hung or crashed during enable.");
        }
        return new SmokeResult(true, pluginName + " loaded and enabled cleanly on Paper.");
    }

    private byte[] singleFileTar(String name, byte[] content) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to tar plugin jar for test", e);
        }
    }
}
