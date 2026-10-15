package com.example.banking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Describes the API for Swagger UI (http://localhost:8081/swagger-ui.html).
 * Every endpoint requires a bearer token unless it is annotated with an empty @SecurityRequirements.
 */
@Configuration
public class OpenApiConfig {

	static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI bankingOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Banking Transaction Management API")
				.version("v1")
				.description("""
						Simulated banking API for learning and portfolio use. **Fictional data only - no real money.**

						1. Call `POST /api/v1/auth/login` with a demo login (e.g. `asha@example.com` / `Demo@12345`).
						2. Copy `accessToken` from the response, click **Authorize** and paste it.
						3. Try the protected endpoints.

						Errors always use the same JSON shape: `timestamp, status, code, message, path, fieldErrors`.
						"""))
			.components(new Components().addSecuritySchemes(BEARER_AUTH,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
