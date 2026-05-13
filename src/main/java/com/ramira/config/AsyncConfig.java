package com.ramira.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated thread pool for the Telegram notification workers.
 *
 * Why a bounded queue?
 *  – Prevents unbounded memory growth under extreme load.
 *  – If the queue fills up the CallerRunsPolicy kicks in:
 *    the calling thread processes the notification itself,
 *    adding back-pressure rather than dropping orders.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.queue.notification-threads:5}")
    private int notificationThreads;

    @Value("${app.queue.capacity:10000}")
    private int queueCapacity;

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(notificationThreads);
        exec.setMaxPoolSize(notificationThreads * 2);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("notif-worker-");
        // Back-pressure: if queue is full, the submitting thread runs the task itself
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    /**
     * Default executor for all other @Async calls.
     * Keeps notification pool isolated.
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("async-default-");
        exec.initialize();
        return exec;
    }
}
