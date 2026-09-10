package com.financedashboard.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the application clock, so use cases that depend on the current day stay testable. */
@Configuration
public class ClockConfiguration {

    /** The system clock in the server's timezone. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
