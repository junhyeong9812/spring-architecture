package com.shoptracker.shared.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator eventBusHealth() {
        return () -> Health.up()
                .withDetail("type", "spring-modulith-events")
                .build();
    }
}