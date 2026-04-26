package com.aniri.workflow_service.application.health;

import com.aniri.workflow_service.application.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SqsHealthIndicator implements HealthIndicator {

    private static final long TIMEOUT_SECONDS = 2;

    private final SqsAsyncClient sqsAsyncClient;
    private final AwsProperties awsProperties;

    @Override
    public Health health() {
        final String queueUrl = awsProperties.sqs().bookingEventsQueueUrl();
        final long start = System.currentTimeMillis();
        try {
            final GetQueueAttributesResponse response = sqsAsyncClient.getQueueAttributes(b -> b
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            final long elapsedMs = System.currentTimeMillis() - start;
            return Health.up()
                    .withDetail("queue-url", queueUrl)
                    .withDetail("approximate-messages",
                            response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                    .withDetail("response-time-ms", elapsedMs)
                    .build();
        } catch (final Exception e) {
            return Health.down(e)
                    .withDetail("queue-url", queueUrl)
                    .withDetail("response-time-ms", System.currentTimeMillis() - start)
                    .build();
        }
    }
}
