package com.aniri.workflow_service.application.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class CognitoHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final String jwkSetUri;
    private final HttpClient httpClient;

    public CognitoHealthIndicator(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") final String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public Health health() {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return Health.unknown().withDetail("reason", "jwk-set-uri not configured").build();
        }
        final long start = System.currentTimeMillis();
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(jwkSetUri))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            final HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            final long elapsedMs = System.currentTimeMillis() - start;
            final int status = response.statusCode();
            final Health.Builder result = (status >= 200 && status < 300) ? Health.up() : Health.down();
            return result
                    .withDetail("jwk-set-uri", jwkSetUri)
                    .withDetail("status-code", status)
                    .withDetail("response-time-ms", elapsedMs)
                    .build();
        } catch (final Exception e) {
            return Health.down(e)
                    .withDetail("jwk-set-uri", jwkSetUri)
                    .withDetail("response-time-ms", System.currentTimeMillis() - start)
                    .build();
        }
    }
}
