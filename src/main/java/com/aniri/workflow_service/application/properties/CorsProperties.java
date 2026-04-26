package com.aniri.workflow_service.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
