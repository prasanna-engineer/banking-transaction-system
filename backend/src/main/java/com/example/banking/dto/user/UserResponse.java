package com.example.banking.dto.user;

import java.time.Instant;

import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.enums.UserStatus;

/** Safe user profile: the password hash is never part of any response. */
public record UserResponse(Long id, String fullName, String email, Role role, UserStatus status, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getStatus(),
				user.getCreatedAt());
	}

}
