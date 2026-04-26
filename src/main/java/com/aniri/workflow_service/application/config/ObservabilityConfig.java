package com.aniri.workflow_service.application.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservedAspect observedAspect(final ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
