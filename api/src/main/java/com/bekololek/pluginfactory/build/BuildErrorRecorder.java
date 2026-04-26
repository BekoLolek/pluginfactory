package com.bekololek.pluginfactory.build;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists failures from the AI/agent layer into build_errors so they
 * show up in the admin error endpoints alongside compiler/security
 * failures from BuildPipelineService.
 *
 * <p>Wrapped with REQUIRES_NEW so a recording failure (DB outage) can't
 * roll back the caller's transaction or mask the original exception.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BuildErrorRecorder {

    private final BuildErrorRepository buildErrorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID sessionId, String category, String severity,
                       String message, Throwable throwable) {
        if (sessionId == null) {
            log.warn("recordSessionError called with null sessionId — dropping error '{}'", message);
            return;
        }
        try {
            BuildError error = new BuildError();
            error.setSessionId(sessionId);
            error.setCategory(category != null ? category : "GENERAL");
            error.setSeverity(severity != null ? severity : "ERROR");
            error.setMessage(message != null ? message : "Unknown error");
            if (throwable != null) {
                error.setStackTrace(stackTrace(throwable));
            }
            error.setRetryCount(0);
            buildErrorRepository.save(error);
        } catch (Exception persistFailure) {
            log.error("Failed to persist build error for session {}: original='{}'",
                    sessionId, message, persistFailure);
        }
    }

    private static String stackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append('\n');
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("\tat ").append(el).append('\n');
            if (sb.length() > 4000) {
                sb.append("\t... truncated");
                break;
            }
        }
        return sb.toString();
    }
}
