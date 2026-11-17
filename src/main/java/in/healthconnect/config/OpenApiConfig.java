package in.healthconnect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Adds an "Authorize" button to Swagger UI: paste the JWT from /api/v1/auth/login and every
// request is sent with "Authorization: Bearer <token>".
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI healthConnectOpenApi() {
        return new OpenAPI()
                .info(new Info().title("HealthConnect API").version("v1")
                        .description("Login at /api/v1/auth/login, then click Authorize and paste the token."))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
