package com.example.banking.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.banking.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Page<Notification> findByUserId(Long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);

	// Ownership is part of the lookup: another user's notification is simply "not found".
	Optional<Notification> findByIdAndUserId(Long id, Long userId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

}
