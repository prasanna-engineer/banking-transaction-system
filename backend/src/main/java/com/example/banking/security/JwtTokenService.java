package com.example.banking.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.banking.enums.Role;

/** Issues signed access tokens. Validating them is done by Spring Security's JwtDecoder. */
@Service
public class JwtTokenService {

	static final String ROLE_CLAIM = "role";

	static final String TOKEN_VERSION_CLAIM = "tv";

	private final JwtEncoder jwtEncoder;

	private final JwtProperties properties;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	/** For a user whose token version was never bumped (tests, new users). */
	public AccessToken createAccessToken(Long userId, Role role) {
		return createAccessToken(userId, role, 0);
	}

	public AccessToken createAccessToken(Long userId, Role role, int tokenVersion) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.accessTokenTtl());
		// Only identifiers go in the token: its payload is Base64, readable by anyone who holds it.
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.claim(ROLE_CLAIM, role.name())
			.claim(TOKEN_VERSION_CLAIM, tokenVersion)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt);
	}

	public record AccessToken(String value, Instant expiresAt) {
	}

}
