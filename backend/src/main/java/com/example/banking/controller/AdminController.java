package com.example.banking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.admin.AuditLogResponse;
import com.example.banking.dto.admin.UpdateUserStatusRequest;
import com.example.banking.dto.user.UserResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.AdminService;

import jakarta.validation.Valid;

/** Everything under /api/v1/admin requires ROLE_ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/users")
	public PageResponse<UserResponse> users(@CurrentUserId Long currentUserId,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminService.listUsers(currentUserId, q, page, size);
	}

	@PatchMapping("/users/{id}/status")
	public UserResponse changeStatus(@CurrentUserId Long currentUserId, @PathVariable Long id,
			@Valid @RequestBody UpdateUserStatusRequest request) {
		return adminService.changeUserStatus(currentUserId, id, request.status());
	}

	@GetMapping("/audit-logs")
	public PageResponse<AuditLogResponse> auditLogs(@CurrentUserId Long currentUserId,
			@RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return adminService.listAuditLogs(currentUserId, action, entityType, page, size);
	}

}
