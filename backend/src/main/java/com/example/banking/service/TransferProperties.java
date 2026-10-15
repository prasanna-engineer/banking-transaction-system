package com.example.banking.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.transfer.* settings from application.yml.
 * maxAmount: largest single transfer. otpThreshold: amounts above this need a one-time code.
 */
@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(BigDecimal maxAmount, BigDecimal otpThreshold) {
}
