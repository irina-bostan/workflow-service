package com.aniri.workflow_service.application.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    /** MDC key under which the current Observation's name is exposed to log appenders. */
    public static final String OBSERVATION_MDC_KEY = "observation";
    private static final String CONTEXT_KEY_PREVIOUS = "_previousObservationName";

    @Bean
    public ObservedAspect observedAspect(final ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Pushes the current Observation's name into MDC so the JSON encoder's {@code <mdc/>}
     * provider emits it as a top-level field. Lets you grep logs by
     * {@code "observation":"booking.create"} to scope to one method's output, in addition
     * to traceId/spanId. Stacks correctly under nested observations: each scope saves
     * the previous MDC value into its own context map and restores on stop.
     */
    @Bean
    public ObservationHandler<Observation.Context> observationNameMdcHandler() {
        return new ObservationHandler<>() {
            @Override
            public void onStart(final Observation.Context context) {
                final String previous = MDC.get(OBSERVATION_MDC_KEY);
                if (previous != null) {
                    context.put(CONTEXT_KEY_PREVIOUS, previous);
                }
                MDC.put(OBSERVATION_MDC_KEY, context.getName());
            }

            @Override
            public void onStop(final Observation.Context context) {
                final Object prev = context.get(CONTEXT_KEY_PREVIOUS);
                if (prev instanceof String s) {
                    MDC.put(OBSERVATION_MDC_KEY, s);
                } else {
                    MDC.remove(OBSERVATION_MDC_KEY);
                }
            }

            @Override
            public boolean supportsContext(final Observation.Context context) {
                return true;
            }
        };
    }
}
