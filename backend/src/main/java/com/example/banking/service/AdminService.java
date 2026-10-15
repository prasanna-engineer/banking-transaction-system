package com.example.banking.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.admin.AuditLogResponse;
import com.example.banking.dto.user.UserResponse;
import com.example.banking.entity.AuditLog;
import com.example.banking.entity.User;
import com.example.banking.enums.UserStatus;
import com.example.banking.exception.ApiException;
import com.example.banking.realtime.LiveEvents;
import com.example.banking.repository.AuditLogRepository;
import com.example.banking.repository.UserRepository;

@Service
public class AdminService {

	private static final Logger log = LoggerFactory.getLogger(AdminService.class);

	private final UserRepository userRepository;

	private final AuditLogRepository auditLogRepository;

	private final CurrentUserService currentUserService;

	private final AuditService auditService;

	private final ApplicationEventPublisher events;

	public AdminService(UserRepository userRepository, AuditLogRepository auditLogRepository,
			CurrentUserService currentUserService, AuditService auditService, ApplicationEventPublisher events) {
		this.userRepository = userRepository;
		this.auditLogRepository = auditLogRepository;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> listUsers(Long currentUserId, String q, int page, int size) {
		requireAdmin(currentUserId);
		var pageable = Paging.of(page, size, Sort.Direction.ASC, "id");
		var users = (q == null || q.isBlank()) ? userRepository.findAll(pageable)
				: userRepository.search(q.trim(), pageable);
		return PageResponse.from(users, UserResponse::from);
	}

	/** Enables or disables a user. Every change is audited; history is never edited silently. */
	@Transactional
	public UserResponse changeUserStatus(Long currentUserId, Long userId, UserStatus newStatus) {
		User admin = requireAdmin(currentUserId);
		if (admin.getId().equals(userId)) {
			throw ApiException.badRequest("CANNOT_CHANGE_OWN_STATUS", "You cannot change your own status.");
		}
		User target = userRepository.findById(userId)
			.orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));

		UserStatus previous = target.getStatus();
		if (previous != newStatus) {
			target.changeStatus(newStatus);
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("email", target.getEmail());
			metadata.put("from", previous.name());
			metadata.put("to", newStatus.name());
			auditService.record(admin, "USER_STATUS_CHANGED", "USER", target.getId(), metadata);
			log.info("Admin {} changed user {} status {} -> {}", admin.getId(), target.getId(), previous, newStatus);
			if (newStatus == UserStatus.DISABLED) {
				// Their open browser tabs are signed out the moment this commits.
				events.publishEvent(new LiveEvents.SessionEnded(target.getId()));
			}
		}
		return UserResponse.from(target);
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> listAuditLogs(Long currentUserId, String action, String entityType,
			int page, int size) {
		requireAdmin(currentUserId);
		Specification<AuditLog> spec = Specification.unrestricted();
		if (action != null && !action.isBlank()) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action.trim()));
		}
		if (entityType != null && !entityType.isBlank()) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType.trim()));
		}
		return PageResponse.from(auditLogRepository.findAll(spec, Paging.of(page, size, Sort.Direction.DESC, "createdAt")),
				AuditLogResponse::from);
	}

	/**
	 * The URL rule (/admin/** needs ROLE_ADMIN) already blocks customers. Checking again here, against
	 * the database rather than the token, is defence in depth: a demoted admin loses access at once.
	 */
	private User requireAdmin(Long currentUserId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		if (!user.isAdmin()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
		}
		return user;
	}

}
