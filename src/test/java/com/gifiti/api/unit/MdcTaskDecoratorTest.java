package com.gifiti.api.unit;

import com.gifiti.api.config.AsyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the MDC-propagation contract for {@code @Async} work.
 *
 * <p>Without the decorator, {@code CorrelationIdFilter}'s correlation ID stays on
 * the request thread and every async log line renders {@code [%X{correlationId}]}
 * as an empty {@code []} — the condition that made the 2026-09-12 email outage
 * impossible to trace back to the registrations it broke.
 */
@DisplayName("AsyncConfig — MDC task decorator")
class MdcTaskDecoratorTest {

    private static final String CORRELATION_ID = "correlationId";

    private final TaskDecorator decorator = new AsyncConfig().mdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("carries the submitting thread's correlation ID into the executing thread")
    void propagatesCorrelationIdAcrossThreads() throws InterruptedException {
        MDC.put(CORRELATION_ID, "abc-123");
        AtomicReference<String> seenInsideTask = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seenInsideTask.set(MDC.get(CORRELATION_ID)));

        // Cleared AFTER decorating: proves the value came from the snapshot taken
        // at submit time rather than from ambient state on the executing thread.
        MDC.clear();
        runOnNewThread(decorated);

        assertThat(seenInsideTask.get())
                .as("async task must observe the correlation ID of the request that submitted it")
                .isEqualTo("abc-123");
    }

    @Test
    @DisplayName("restores the pool thread's prior context so reused threads do not leak")
    void restoresPriorContextAfterTask() throws InterruptedException {
        MDC.put(CORRELATION_ID, "submitting-request");
        Runnable decorated = decorator.decorate(() -> { });
        AtomicReference<String> afterTask = new AtomicReference<>();

        runOnNewThread(() -> {
            MDC.put(CORRELATION_ID, "pre-existing-on-pool-thread");
            decorated.run();
            afterTask.set(MDC.get(CORRELATION_ID));
        });

        assertThat(afterTask.get())
                .as("decorator must hand the pool thread back exactly as it found it")
                .isEqualTo("pre-existing-on-pool-thread");
    }

    @Test
    @DisplayName("does not leak a stale pool-thread ID into a task submitted without context")
    void clearsContextWhenSubmitterHadNone() throws InterruptedException {
        MDC.clear();
        AtomicReference<String> seenInsideTask = new AtomicReference<>("unset");
        Runnable decorated = decorator.decorate(() -> seenInsideTask.set(MDC.get(CORRELATION_ID)));

        runOnNewThread(() -> {
            MDC.put(CORRELATION_ID, "stale-from-previous-task");
            decorated.run();
        });

        assertThat(seenInsideTask.get())
                .as("a task submitted with no correlation ID must not inherit the previous task's")
                .isNull();
    }

    private static void runOnNewThread(Runnable runnable) throws InterruptedException {
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
    }
}
