package com.example.banking.dto.admin;

import com.example.banking.enums.UserStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
