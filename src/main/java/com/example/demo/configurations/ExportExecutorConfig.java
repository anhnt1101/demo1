package com.example.demo.configurations;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class ExportExecutorConfig {

    @Value("${export.max-concurrent-workers:2}")
    private int maxWorkers;

    @Bean("exportTaskExecutor")
    public ThreadPoolTaskExecutor
    exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxWorkers);

        executor.setMaxPoolSize(maxWorkers);

        /*
         * Không queue trong Java.
         * EXPORT_REQUEST chính là queue.
         */
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("export-worker-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}