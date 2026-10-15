package com.example.banking.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.support.TestFixtures;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private JwtProperties jwtProperties;

	private User customer;

	private User admin;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		customer = fixtures.user("Jwt Customer", Role.CUSTOMER);
		admin = fixtures.user("Jwt Admin", Role.ADMIN);
		fixtures.flushAndClear();
	}

	@Test
	void requestWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/accounts"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void validTokenIsAccepted() throws Exception {
		mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(validToken(customer))))
			.andExpect(status().isOk());
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		String expired = sign(jwtEncoder, claims(customer).issuedAt(past.minusSeconds(60)).expiresAt(past).build());

		expectInvalidToken(expired);
	}

	@Test
	void tokenWithEditedPayloadIsRejected() throws Exception {
		// A JWT is header.payload.signature. Anyone can decode and edit the payload (it is only Base64),
		// e.g. to promote themselves to ADMIN - but the original signature no longer matches it.
		String[] parts = validToken(customer).split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		String promoted = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.replace("\"CUSTOMER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));

		expectInvalidToken(parts[0] + "." + promoted + "." + parts[2]);
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		JwtEncoder attackerEncoder = NimbusJwtEncoder
			.withSecretKey(new SecretKeySpec("attacker-key-that-is-long-enough-123456".getBytes(StandardCharsets.UTF_8),
					"HmacSHA256"))
			.algorithm(MacAlgorithm.HS256)
			.build();
		// The attacker even claims to be an admin; without our key the token is worthless.
		String forged = sign(attackerEncoder, claims(customer).claim("role", "ADMIN").build());

		expectInvalidToken(forged);
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() throws Exception {
		expectInvalidToken(sign(jwtEncoder, claims(customer).issuer("someone-else").build()));
	}

	@Test
	void customerIsForbiddenFromAdminEndpoints() throws Exception {
		mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(validToken(customer))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void adminPassesTheAdminRule() throws Exception {
		mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(validToken(admin))))
			.andExpect(status().isOk());
	}

	@Test
	void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/accounts").header(HttpHeaders.ORIGIN, "http://localhost:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

		mockMvc.perform(options("/api/v1/accounts").header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isForbidden());
	}

	private void expectInvalidToken(String token) throws Exception {
		mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	private String validToken(User user) {
		return tokenService.createAccessToken(user.getId(), user.getRole()).value();
	}

	private JwtClaimsSet.Builder claims(User user) {
		Instant now = Instant.now();
		return JwtClaimsSet.builder()
			.issuer(jwtProperties.issuer())
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plusSeconds(600))
			.claim("role", user.getRole().name());
	}

	private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

}
