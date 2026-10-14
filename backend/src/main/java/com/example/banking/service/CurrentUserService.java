package com.example.banking.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.UserRepository;

@Service
public class CurrentUserService {

	private final UserRepository userRepository;

	public CurrentUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** Loads the caller; unknown or disabled users are treated as not authenticated. */
	@Transactional(readOnly = true)
	public User requireActiveUser(Long userId) {
		return userRepository.findById(userId).filter(User::isActive).orElseThrow(ApiException::unauthenticated);
	}

}
