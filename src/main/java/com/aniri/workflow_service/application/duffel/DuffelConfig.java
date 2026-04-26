package com.aniri.workflow_service.application.duffel;

import com.aniri.workflow_service.application.properties.DuffelProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "duffel.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DuffelConfig {

    private final DuffelProperties duffelProperties;

    @Bean
    public RestClient duffelRestClient() {
        // Duffel responses include many fields we don't model (created_at, expires_at,
        // tax_amount, passenger_identity_documents_required, etc.). Without disabling this,
        // a single new/unmodeled field on Duffel's side breaks every search with an
        // UnrecognizedPropertyException — the global Spring Jackson config doesn't apply
        // here because this RestClient uses its own dedicated ObjectMapper.
        var mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        return RestClient.builder()
                .baseUrl("https://api.duffel.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + duffelProperties.apiToken())
                .defaultHeader("Duffel-Version", "v2")
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
                .build();
    }
}
