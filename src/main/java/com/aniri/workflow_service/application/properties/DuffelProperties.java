package com.aniri.workflow_service.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duffel")
public record DuffelProperties(
        boolean enabled,
        String apiToken,
        String defaultOrigin
) {
}
