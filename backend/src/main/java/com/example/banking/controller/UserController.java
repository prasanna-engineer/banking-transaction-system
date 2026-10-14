package com.example.banking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.user.UserResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.CurrentUserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final CurrentUserService currentUserService;

	public UserController(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	@GetMapping("/me")
	public UserResponse me(@CurrentUserId Long currentUserId) {
		return currentUserService.getProfile(currentUserId);
	}

}
