package com.bonifybank.bonify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs, e.g. the nightly cleanup of expired refresh tokens. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
