package com.example.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.security.JwtTokenService;
import com.example.banking.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Step-up authentication: transfers above ₹50,000 need a one-time code bound to that exact transfer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransferOtpTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JwtTokenService tokenService;

	private User asha;

	private String bigTransfer;

	private String smallTransfer;

	@BeforeEach
	void setUp() {
		TestFixtures fixtures = new TestFixtures(entityManager);
		asha = fixtures.user("Asha Otp", Role.CUSTOMER);
		User ravi = fixtures.user("Ravi Otp", Role.CUSTOMER);
		BankAccount source = fixtures.fundedAccount(asha, "110000000001", "200000.00");
		BankAccount destination = fixtures.fundedAccount(ravi, "110000000002", "0.00");
		fixtures.flushAndClear();
		bigTransfer = body(source, destination, "60000.00");
		smallTransfer = body(source, destination, "50000.00");
	}

	@Test
	void transfersUpToTheThresholdNeedNoCode() throws Exception {
		transfer(smallTransfer, null, null).andExpect(status().isCreated());
	}

	@Test
	void aLargeTransferWithoutACodeIsRefused() throws Exception {
		transfer(bigTransfer, null, null).andExpect(status().isPreconditionRequired())
			.andExpect(jsonPath("$.code").value("OTP_REQUIRED"));
	}

	@Test
	void aLargeTransferGoesThroughWithTheRightCodeOnce() throws Exception {
		String[] otp = requestOtp(bigTransfer);

		transfer(bigTransfer, otp[0], otp[1]).andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value("60000.00"));
		// The code is single-use.
		transfer(bigTransfer, otp[0], otp[1]).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"));
	}

	@Test
	void wrongCodesCountDownAndThenTheChallengeIsGone() throws Exception {
		String[] otp = requestOtp(bigTransfer);
		String wrong = otp[1].equals("000000") ? "111111" : "000000";

		transfer(bigTransfer, otp[0], wrong).andExpect(jsonPath("$.message").value("Wrong code. 2 attempt(s) left."));
		transfer(bigTransfer, otp[0], wrong).andExpect(jsonPath("$.message").value("Wrong code. 1 attempt(s) left."));
		transfer(bigTransfer, otp[0], wrong).andExpect(jsonPath("$.message").value("Too many wrong codes. Request a new one."));
		// Even the right code no longer works: the attacker had 3 guesses out of a million.
		transfer(bigTransfer, otp[0], otp[1]).andExpect(status().isBadRequest());
	}

	@Test
	void aCodeIsOnlyValidForTheTransferItWasIssuedFor() throws Exception {
		String[] otp = requestOtp(bigTransfer);
		String differentAmount = bigTransfer.replace("60000.00", "90000.00");

		transfer(differentAmount, otp[0], otp[1]).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("This code was issued for a different transfer. Request a new one."));
	}

	@Test
	void anAmountAboveTheLimitIsRejectedBeforeAnyCodeIsAskedFor() throws Exception {
		transfer(bigTransfer.replace("60000.00", "2000000.00"), null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AMOUNT_LIMIT_EXCEEDED"));
	}

	private String[] requestOtp(String body) throws Exception {
		String response = mockMvc.perform(post("/api/v1/transfers/otp").header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresInSeconds").value(300))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new String[] { JsonPath.read(response, "$.challengeId"), JsonPath.read(response, "$.demoCode") };
	}

	private ResultActions transfer(String body, String challengeId, String code) throws Exception {
		var request = post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (challengeId != null) {
			request.header("X-OTP-Challenge", challengeId).header("X-OTP-Code", code);
		}
		return mockMvc.perform(request);
	}

	private static String body(BankAccount source, BankAccount destination, String amount) {
		return """
				{"sourceAccountId":%d,"destinationAccountId":%d,"amount":"%s"}
				""".formatted(source.getId(), destination.getId(), amount);
	}

	private String bearer() {
		return "Bearer " + tokenService.createAccessToken(asha.getId(), asha.getRole()).value();
	}

}
