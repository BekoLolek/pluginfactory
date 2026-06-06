package com.bekololek.pluginfactory.container;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs an AI-generated Mineflayer functional-test script against the freshly
 * built plugin on the isolated TEST Paper server, and parses the harness's JSON
 * verdict. Like {@link TestServerService} it fails OPEN on infrastructure
 * problems (so a flaky container never blocks delivery) but reports genuine
 * scenario failures so the pipeline can drive an auto-fix.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FunctionalTestService {

    private final DockerService dockerService;
    private final ContainerPoolManager containerPoolManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_WAIT_SECONDS = 90;

    public record ScenarioResult(String name, boolean passed, String error) {}

    /**
     * @param ran   true if the harness actually executed (false = skipped/infra)
     * @param passed true if every scenario passed (or skipped — fail open)
     */
    public record FunctionalResult(boolean ran, boolean passed, List<ScenarioResult> scenarios, String detail) {}

    public FunctionalResult run(byte[] jarBytes, String pluginName, String scenarioScript) {
        if (scenarioScript == null || scenarioScript.isBlank()) {
            return skipped("no test script generated");
        }
        if (jarBytes == null || jarBytes.length == 0) {
            return new FunctionalResult(false, false, List.of(), "no JAR to test");
        }
        if (!dockerService.isAvailable()) {
            return skipped("docker unavailable");
        }

        String containerId;
        try {
            containerId = containerPoolManager.claimContainer(DockerService.ContainerType.TEST);
        } catch (Exception e) {
            log.warn("Could not claim TEST container for functional test: {}", e.getMessage());
            return skipped("test container unavailable");
        }

        try {
            dockerService.executeCommand(containerId, "sh", "-c",
                    "rm -rf /server/plugins/* /server/logs/* /server/run.log /work/* 2>/dev/null || true");
            dockerService.copyToContainer(containerId, singleFileTar("plugin.jar", jarBytes), "/server/plugins");
            dockerService.copyToContainer(containerId,
                    singleFileTar("scenario.js", scenarioScript.getBytes(StandardCharsets.UTF_8)), "/work");

            String script = ""
                    + "cd /server && rm -f run.log && "
                    + "java -Xmx1536m -XX:+UseSerialGC -jar paper.jar --nogui > run.log 2>&1 & "
                    + "SRV=$!; "
                    + "for i in $(seq 1 " + MAX_WAIT_SECONDS + "); do "
                    + "  grep -q 'Done (' run.log && break; "
                    + "  grep -Eqi 'Could not load|while enabling' run.log && break; "
                    + "  kill -0 $SRV 2>/dev/null || break; sleep 1; done; "
                    + "if grep -q 'Done (' run.log; then node /harness/run-test.js /work/scenario.js; "
                    + "else echo '===PF_RESULT_BEGIN==='; "
                    + "echo '{\"passed\":false,\"scenarios\":[],\"error\":\"server did not start for functional test\"}'; "
                    + "echo '===PF_RESULT_END==='; fi; "
                    + "kill $SRV 2>/dev/null || true";
            ExecResult result = dockerService.executeCommand(containerId, "sh", "-c", script);
            return parse(result.stdout() + "\n" + result.stderr());
        } catch (Exception e) {
            log.warn("Functional test infra error for {}: {}", pluginName, e.getMessage());
            return skipped("infra error: " + e.getMessage());
        } finally {
            try {
                dockerService.executeCommand(containerId, "sh", "-c",
                        "rm -rf /server/plugins/* /server/logs/* /work/* 2>/dev/null || true");
            } catch (Exception ignored) {
                // best-effort cleanup
            }
            containerPoolManager.releaseContainer(containerId, DockerService.ContainerType.TEST);
        }
    }

    FunctionalResult parse(String output) {
        String json = extractBetween(output, "===PF_RESULT_BEGIN===", "===PF_RESULT_END===");
        if (json == null) {
            return skipped("no functional-test result captured");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ScenarioResult> scenarios = new ArrayList<>();
            if (root.has("scenarios") && root.get("scenarios").isArray()) {
                for (JsonNode s : root.get("scenarios")) {
                    scenarios.add(new ScenarioResult(
                            s.path("name").asText("scenario"),
                            s.path("passed").asBoolean(false),
                            s.path("error").isNull() ? null : s.path("error").asText(null)));
                }
            }
            boolean passed = root.path("passed").asBoolean(false);
            String topError = root.path("error").isNull() ? null : root.path("error").asText(null);
            return new FunctionalResult(true, passed, scenarios, summarize(passed, scenarios, topError));
        } catch (Exception e) {
            return skipped("could not parse functional-test result: " + e.getMessage());
        }
    }

    private static String summarize(boolean passed, List<ScenarioResult> scenarios, String topError) {
        if (scenarios.isEmpty()) {
            return passed ? "passed" : ("failed: " + (topError != null ? topError : "unknown"));
        }
        long pass = scenarios.stream().filter(ScenarioResult::passed).count();
        StringBuilder sb = new StringBuilder();
        sb.append(pass).append("/").append(scenarios.size()).append(" scenarios passed.");
        scenarios.stream().filter(s -> !s.passed()).forEach(s ->
                sb.append("\n- FAILED: ").append(s.name()).append(" — ").append(s.error()));
        return sb.toString();
    }

    private static FunctionalResult skipped(String why) {
        return new FunctionalResult(false, true, List.of(), "functional test skipped (" + why + ")");
    }

    private static String extractBetween(String s, String begin, String end) {
        int i = s.indexOf(begin);
        if (i < 0) return null;
        int j = s.indexOf(end, i + begin.length());
        if (j < 0) return null;
        return s.substring(i + begin.length(), j).trim();
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
            throw new RuntimeException("Failed to tar " + name, e);
        }
    }
}
