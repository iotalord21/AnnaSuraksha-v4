package com.rationchain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

/**
 * Async configuration — enables @Async for AuditLogService writes.
 *
 * Thread pool sizing:
 *   - Core:  4 threads  (always alive — handles normal audit log throughput)
 *   - Max:   8 threads  (bursts during fraud scan runs)
 *   - Queue: 200        (deep queue prevents drops during spikes)
 *
 * At national scale this would be replaced with a Kafka topic "audit-events"
 * consumed by a dedicated audit microservice, giving durable async writes
 * without blocking the request thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            // If queue full: run on caller thread rather than drop the audit entry
            if (!poolExecutor.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return auditExecutor();
    }
}
