package com.ratedistribution.rdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AsyncConfig {
    @Bean(name = "rateUpdateExecutor")
    public ThreadPoolTaskExecutor rateUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);  // Minimum 8 iş parçacığı (Güçlü paralellik)
        executor.setMaxPoolSize(16);  // Maksimum 16 iş parçacığı
        executor.setQueueCapacity(100); // 100 kadar işlemi sıraya alabilir
        executor.setThreadNamePrefix("RateUpdate-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // 4 paralel task için thread havuzu
        scheduler.setThreadNamePrefix("RateScheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
