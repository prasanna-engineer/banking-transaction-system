package com.example.banking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.user.UserResponse;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.UserRepository;

@Service
public class CurrentUserService {

	private final UserRepository userRepository;

	public CurrentUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse getProfile(Long userId) {
		return UserResponse.from(requireActiveUser(userId));
	}

	/**
	 * Loads the caller; unknown or disabled users are treated as not authenticated. Checked on every
	 * request, so disabling a user locks them out immediately even while their token has not expired.
	 */
	@Transactional(readOnly = true)
	public User requireActiveUser(Long userId) {
		return userRepository.findById(userId).filter(user -> user.isActive()).orElseThrow(ApiException::unauthenticated);
	}

}
