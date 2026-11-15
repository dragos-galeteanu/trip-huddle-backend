package com.triphuddle.api.destination.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock the service reads the time from, so tests can fix it.
 */
@Configuration
public class ClockConfig {

    /**
     * The system clock, in UTC.
     *
     * @return the clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
