package com.example.banking.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.example.banking.repository.UserRepository;

/**
 * Rejects an access token whose "tv" (token version) claim no longer matches the user's current
 * token_version - i.e. tokens issued before the user chose "sign out of all devices".
 * Costs one tiny query per request; that is the price of being able to revoke stateless tokens.
 */
@Component
public class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error REVOKED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"This token has been revoked.", null);

	private final UserRepository userRepository;

	public TokenVersionValidator(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		Object claim = jwt.getClaim(JwtTokenService.TOKEN_VERSION_CLAIM);
		if (!(claim instanceof Number tokenVersion) || jwt.getSubject() == null) {
			return OAuth2TokenValidatorResult.failure(REVOKED);
		}
		long userId;
		try {
			userId = Long.parseLong(jwt.getSubject());
		}
		catch (NumberFormatException ex) {
			return OAuth2TokenValidatorResult.failure(REVOKED);
		}
		return userRepository.findTokenVersion(userId)
			.filter(current -> current == tokenVersion.intValue())
			.map(current -> OAuth2TokenValidatorResult.success())
			.orElse(OAuth2TokenValidatorResult.failure(REVOKED));
	}

}
