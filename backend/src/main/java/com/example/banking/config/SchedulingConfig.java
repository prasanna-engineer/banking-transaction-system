package com.example.banking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (SSE heartbeats, clean-up, scheduled transfers) - like Laravel's task scheduler. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
