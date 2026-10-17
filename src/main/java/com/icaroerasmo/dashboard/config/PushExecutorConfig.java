package com.icaroerasmo.dashboard.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for Web Push sends. Web Push notifications make synchronous
 * HTTP calls to browser vendors' push services; running them on the RabbitMQ
 * listener thread used to block message consumption (and trigger RabbitMQ's
 * consumer-acknowledgement timeout). This bounded executor isolates those calls.
 */
@Log4j2
@Configuration
public class PushExecutorConfig {

    @Bean("pushExecutor")
    public ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("web-push-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // Never block the caller: if the pool is saturated, drop the notification
        // and log it rather than stalling the message listener.
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("Web push executor saturated; dropping a push notification"));
        executor.initialize();
        return executor;
    }
}
