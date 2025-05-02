package com.ratedistribution.rdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * AsyncConfig defines the asynchronous execution and scheduling configurations used across the application.
 * It provides a custom ThreadPoolTaskExecutor for rate updates and a ThreadPoolTaskScheduler for scheduled tasks.
 * These beans enable efficient handling of concurrent and scheduled operations.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Configuration
public class AsyncConfig {
    @Bean(name = "rateUpdateExecutor")
    public ThreadPoolTaskExecutor rateUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("RateUpdate-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("RateScheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
