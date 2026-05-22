package com.bekololek.pluginfactory.common.config;

import com.bekololek.pluginfactory.common.logging.MdcAsyncTaskDecorator;
import com.bekololek.pluginfactory.email.EmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated thread pool for build pipeline work.
 *
 * <p>Build pipelines are long-running (LLM generation + Maven compile +
 * container exec + artifact upload) and we never want them sharing a
 * pool with HTTP request threads or Spring's default
 * {@code SimpleAsyncTaskExecutor}, which spawns an unbounded new thread
 * per call. Both would lead to either request starvation or a thread
 * explosion under load.
 *
 * <p>The sizing below is intentionally conservative: two concurrent
 * builds in steady state, bursting to four, with a small queue to
 * absorb spikes. A full queue throws {@code TaskRejectedException}
 * which propagates back to the caller as a 500 — preferable to
 * silently letting the queue grow without bound.
 *
 * <p>Pool sizes are tuned for a single-instance deployment. If we ever
 * run multiple API replicas we should move this to an external queue
 * (see {@code BuildRecoveryService} javadoc for the longer-term plan).
 */
@Configuration
public class AsyncConfig {

    public static final String BUILD_EXECUTOR = "buildPipelineExecutor";

    @Bean(name = BUILD_EXECUTOR)
    public TaskExecutor buildPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("build-pipeline-");
        // Carry MDC (requestId, userId, sessionId) from the submitting
        // thread into the worker so async build logs stay correlatable
        // to the originating request.
        executor.setTaskDecorator(new MdcAsyncTaskDecorator());
        // Graceful shutdown: on SIGTERM we wait up to two minutes for
        // in-flight builds to finish before the JVM exits. Anything
        // still running after that will be picked up by
        // BuildRecoveryService on the next startup.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    @Bean(name = EmailService.EMAIL_EXECUTOR)
    public TaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
