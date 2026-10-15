package com.example.banking.config;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsArePublicAndDescribeEveryEndpointAndTheJwtScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Banking Transaction Management API"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.paths['/api/v1/transfers'].post").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/users'].get").exists())
			// Public endpoints are marked as needing no token; everything else inherits bearerAuth.
			.andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/accounts'].get.security").doesNotExist());
	}

	@Test
	void swaggerUiIsReachableWithoutLoggingIn() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
			.andExpect(status().is(anyOf(is(200), is(302))));
	}

}
