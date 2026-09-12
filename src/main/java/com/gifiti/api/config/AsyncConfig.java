package com.gifiti.api.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Propagates the SLF4J {@link MDC} across the {@code @Async} boundary.
 *
 * <p>{@code MDC} is thread-local. {@link CorrelationIdFilter} populates it on the
 * request thread, but {@code @Async} hands the work to a pool thread
 * ({@code task-N}) that inherits nothing. Every async log line rendered with the
 * {@code [%X{correlationId}]} console pattern therefore came out with an empty
 * {@code []} and could not be traced back to the request that triggered it —
 * which is exactly how the 2026-09-12 invalid-Resend-key outage was diagnosed
 * from a log line that could not be tied to a registration.
 *
 * <p>Spring Boot's {@code TaskExecutorConfigurations} applies a single
 * {@link TaskDecorator} bean to the auto-configured {@code applicationTaskExecutor},
 * so declaring this bean is sufficient — no custom executor is required.
 */
@Configuration
public class AsyncConfig {

    /**
     * Copies the submitting thread's MDC into the executing pool thread, then
     * restores whatever that pool thread previously held.
     *
     * <p>The restore step matters because pool threads are reused: without it a
     * task would leak its correlation ID into the next, unrelated task that the
     * same thread happens to pick up.
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> submitterContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> priorContext = MDC.getCopyOfContextMap();
                setContextOrClear(submitterContext);
                try {
                    runnable.run();
                } finally {
                    setContextOrClear(priorContext);
                }
            };
        };
    }

    private static void setContextOrClear(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
