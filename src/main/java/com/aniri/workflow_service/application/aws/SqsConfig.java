package com.aniri.workflow_service.application.aws;

import com.aniri.workflow_service.application.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "aws.sqs.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SqsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(awsProperties.region() != null ? awsProperties.region() : "eu-west-1"));

        String endpointOverride = awsProperties.endpointOverride();
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
            var creds = awsProperties.credentials();
            String accessKey = creds != null && creds.accessKey() != null ? creds.accessKey() : "test";
            String secretKey = creds != null && creds.secretKey() != null ? creds.secretKey() : "test";
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            ));
        }

        return builder.build();
    }
}
