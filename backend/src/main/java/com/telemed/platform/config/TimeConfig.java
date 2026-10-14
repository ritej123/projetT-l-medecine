package com.telemed.platform.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId appointmentZone(@Value("${app.appointment.zone-id:Europe/Paris}") String zoneId) {
        return ZoneId.of(zoneId);
    }
}
