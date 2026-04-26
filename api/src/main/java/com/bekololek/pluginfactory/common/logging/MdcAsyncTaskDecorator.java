package com.bekololek.pluginfactory.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Captures the submitting thread's MDC at task-submission time and
 * restores it on the worker thread for the duration of the task. Without
 * this, async build-pipeline logs are stripped of {@code requestId} and
 * {@code userId} that the originating HTTP request set.
 */
public class MdcAsyncTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> snapshot = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (snapshot != null) {
                MDC.setContextMap(snapshot);
            } else {
                MDC.clear();
            }
            try {
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
