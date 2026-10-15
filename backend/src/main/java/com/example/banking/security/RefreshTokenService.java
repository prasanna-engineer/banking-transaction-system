package com.example.banking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.RefreshToken;
import com.example.banking.entity.User;
import com.example.banking.repository.RefreshTokenRepository;

/**
 * Refresh tokens: random 256-bit values, stored only as SHA-256 hashes, valid once.
 *
 * Rotation + reuse detection: every refresh retires the presented token and hands out a new one from the
 * same "family". If a retired token is ever presented again, someone has a copy (it was stolen), so the
 * whole family is revoked and both the thief and the real user must sign in again.
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository repository;

	private final JwtProperties properties;

	public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	/** Starts a new family (a fresh login). Returns the raw token, which is never stored. */
	@Transactional(propagation = Propagation.MANDATORY)
	public IssuedRefreshToken issue(User user) {
		return issueInFamily(user, UUID.randomUUID().toString());
	}

	/**
	 * Swaps a valid token for a new one. Empty if the token is unknown, expired or already used - in the
	 * "already used" case the whole family is revoked first.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Rotation> rotate(String rawToken) {
		Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		RefreshToken token = found.get();
		if (token.isRevoked()) {
			log.warn("Refresh token reuse detected for user {}; revoking the token family", token.getUser().getId());
			repository.revokeFamily(token.getFamilyId(), Instant.now());
			return Optional.empty();
		}
		if (token.isExpired(Instant.now())) {
			return Optional.empty();
		}
		token.revoke();
		return Optional.of(new Rotation(token.getUser(), issueInFamily(token.getUser(), token.getFamilyId())));
	}

	/** Sign out of this session: revokes the token's whole family. Unknown tokens are ignored. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void revokeFamilyOf(String rawToken) {
		repository.findByTokenHash(hash(rawToken)).ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void revokeAll(Long userId) {
		repository.revokeAllForUser(userId, Instant.now());
	}

	private IssuedRefreshToken issueInFamily(User user, String familyId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
		repository.save(new RefreshToken(user, familyId, hash(raw), expiresAt));
		return new IssuedRefreshToken(raw, expiresAt);
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
		}
	}

	public record IssuedRefreshToken(String value, Instant expiresAt) {
	}

	public record Rotation(User user, IssuedRefreshToken newToken) {
	}

}
