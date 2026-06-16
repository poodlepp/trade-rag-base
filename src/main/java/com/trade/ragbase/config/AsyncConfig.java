package com.trade.ragbase.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean("indexTaskExecutor")
    public Executor indexTaskExecutor(
            @Value("${spring.task.execution.pool.core-size:4}") int corePoolSize,
            @Value("${spring.task.execution.pool.max-size:8}") int maxPoolSize,
            @Value("${spring.task.execution.pool.queue-capacity:200}") int queueCapacity,
            @Value("${spring.task.execution.thread-name-prefix:index-task-}") String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
