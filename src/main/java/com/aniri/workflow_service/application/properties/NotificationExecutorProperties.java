package com.aniri.workflow_service.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pool sizing for the {@code notificationExecutor} used by {@code @Async} tasks.
 * Override per environment via {@code notification.executor.*} or env vars
 * (e.g. {@code NOTIFICATION_EXECUTOR_MAX_POOL_SIZE=50}).
 */
@ConfigurationProperties(prefix = "notification.executor")
public record NotificationExecutorProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int awaitTerminationSeconds
) {
}
