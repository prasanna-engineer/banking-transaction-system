package com.example.banking.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the app.jwt.* settings in application.yml (like a Laravel config/jwt.php file). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
}
