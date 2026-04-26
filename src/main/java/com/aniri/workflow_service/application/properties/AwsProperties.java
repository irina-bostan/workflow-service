package com.aniri.workflow_service.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        String endpointOverride,
        Credentials credentials,
        Sqs sqs
) {
    public record Credentials(String accessKey, String secretKey) {}

    public record Sqs(boolean enabled, String bookingEventsQueueUrl) {}
}
