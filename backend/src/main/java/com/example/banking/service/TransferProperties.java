package com.example.banking.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.transfer.* settings from application.yml. */
@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(BigDecimal maxAmount) {
}
