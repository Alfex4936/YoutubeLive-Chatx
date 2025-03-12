package csw.youtube.chat.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// http://localhost:8080/swagger-ui/index.html#/ -> Swagger UI
@Configuration
public class OpenApiConfig {

    @Bean
    @Profile("prod") // Only active in the "prod" profile
    public OpenAPI openAPIProd() {
        Info info = new Info()
                .title("ytChatX API")
                .version("1.0.0")
                .description("ytChatX API 데모")
                .termsOfService("https://smartbear.com/terms-of-use/")
                .contact(new Contact().name("SeokWon Choi").url("https://github.com/Alfex4936").email("ikr@kakao.com"))
                .license(new License().name("Apache License Version 2.0").url("http://www.apache.org/licenses/LICENSE-2.0"));

        SecurityScheme securityScheme = new SecurityScheme()
                .name("Bearer Authentication")
                .type(SecurityScheme.Type.HTTP) // Use SecurityScheme.Type
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("Bearer Authentication", securityScheme))
                .info(info);
    }

    @Bean
    @Profile("!prod") // Active in all profiles except "prod"
    public OpenAPI openAPILocal() {
        Info info = new Info()
                .title("ytChatX API")
                .version("1.0.0")
                .description("ytChatX API 데모")
                .termsOfService("https://smartbear.com/terms-of-use/")
                .contact(new Contact().name("SeokWon Choi").url("https://github.com/Alfex4936").email("ikr@kakao.com"))
                .license(new License().name("Apache License Version 2.0").url("http://www.apache.org/licenses/LICENSE-2.0"));

        return new OpenAPI()
                .components(new Components())
                .info(info);
    }
}