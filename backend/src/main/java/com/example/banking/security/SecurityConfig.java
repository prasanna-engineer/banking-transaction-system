package com.example.banking.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final int MIN_SECRET_BYTES = 32;

	/**
	 * Every request passes through this chain of filters before reaching a controller
	 * (similar to Laravel's middleware stack, e.g. auth:sanctum on a route group).
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthErrorHandler errorHandler) throws Exception {
		http
			// CSRF protects cookie-based sessions. We use no cookies: the browser never sends our token
			// automatically, so a forged cross-site request cannot carry it. Revisit if tokens move to cookies.
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			// Stateless: no server session; every request must bring its own token.
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
						"/api/v1/auth/logout")
				.permitAll()
				.requestMatchers("/error").permitAll()
				// The live stream is authenticated by a single-use ticket checked in NotificationController,
				// because the browser's EventSource cannot send an Authorization header.
				.requestMatchers(HttpMethod.GET, "/api/v1/notifications/stream").permitAll()
				// API documentation. The docs describe endpoints only; every endpoint still enforces its own rules.
				// Turn off in a real deployment with API_DOCS_ENABLED=false.
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				// Admins inspect activity; only customers move money (project guide, section 6).
				.requestMatchers(HttpMethod.POST, "/api/v1/transfers").hasRole("CUSTOMER")
				.requestMatchers("/api/v1/payees/**").hasRole("CUSTOMER")
				.anyRequest().authenticated())
			// Validates "Authorization: Bearer <jwt>" on every request using the JwtDecoder bean below.
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(errorHandler)
				.accessDeniedHandler(errorHandler))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler));
		return http.build();
	}

	/** Turns the token's "role" claim (e.g. "ADMIN") into the authority ROLE_ADMIN used by hasRole("ADMIN"). */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtTokenService.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		byte[] bytes = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES
					+ " bytes long");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	/**
	 * Rejects tokens with a bad signature, a different algorithm, an expired "exp" or a wrong issuer - and,
	 * when the full application is running, tokens issued before the user's last "sign out everywhere".
	 */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties,
			ObjectProvider<TokenVersionValidator> tokenVersionValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.issuer());
		TokenVersionValidator versionCheck = tokenVersionValidator.getIfAvailable();
		decoder.setJwtValidator(versionCheck == null ? standard : new DelegatingOAuth2TokenValidator<>(standard, versionCheck));
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		// Response headers the browser's JavaScript may read (all others are hidden cross-origin).
		config.setExposedHeaders(List.of("Content-Disposition", "Retry-After", "Idempotent-Replayed"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
