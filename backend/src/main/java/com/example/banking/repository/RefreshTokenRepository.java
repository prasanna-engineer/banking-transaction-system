package com.example.banking.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.banking.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update RefreshToken r set r.revokedAt = :now where r.familyId = :familyId and r.revokedAt is null")
	int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

	@Modifying
	@Query("update RefreshToken r set r.revokedAt = :now where r.user.id = :userId and r.revokedAt is null")
	int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

	@Modifying
	@Query("delete from RefreshToken r where r.expiresAt < :cutoff")
	int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

}
