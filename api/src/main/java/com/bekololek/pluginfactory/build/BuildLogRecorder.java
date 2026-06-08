package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Persists raw build-step output (Maven compile, Paper runtime, functional
 * test) into build_logs so the admin dashboard can show the actual terminal
 * output of any build.
 *
 * <p>Like {@link BuildErrorRecorder}, runs in its own REQUIRES_NEW transaction
 * so a logging failure can never roll back or mask the build itself. ANSI
 * colour codes are stripped and content is capped so a runaway log can't bloat
 * the row.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BuildLogRecorder {

    /** Cap stored content; build logs are normally well under this. */
    static final int MAX_CHARS = 100_000;

    // Real ANSI sequences only: ESC (0x1B) then CSI params. Anchoring on ESC
    // avoids eating legitimate bracketed text like "[INFO]" / "[Server]".
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;]*[A-Za-z]");

    private final BuildLogRepository buildLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID sessionId, UUID iterationId, String phase,
                       Integer exitCode, String content) {
        if (sessionId == null || content == null || content.isBlank()) {
            return;
        }
        try {
            BuildLog logRow = new BuildLog();
            logRow.setSessionId(sessionId);
            logRow.setIterationId(iterationId);
            logRow.setPhase(phase != null ? phase : "GENERAL");
            logRow.setExitCode(exitCode);
            logRow.setContent(clean(content));
            buildLogRepository.save(logRow);
        } catch (Exception e) {
            log.warn("Failed to persist build log for session {} ({}): {}",
                    sessionId, phase, e.getMessage());
        }
    }

    /** Strip ANSI escapes and cap length (keeping the most recent tail). */
    static String clean(String raw) {
        String stripped = ANSI.matcher(raw).replaceAll("");
        if (stripped.length() <= MAX_CHARS) {
            return stripped;
        }
        // Keep the tail — failures and summaries land at the end of the output.
        return "…(truncated " + (stripped.length() - MAX_CHARS) + " chars)…\n"
                + stripped.substring(stripped.length() - MAX_CHARS);
    }
}
