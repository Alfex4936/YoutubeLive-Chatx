package csw.youtube.chat.common.config.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final ObjectMapper objectMapper;

    // Whitelist (no authentication)
    private static final String[] WHITE_LIST_URL = {
            "/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/stripe/**"
    };
    // Endpoints under /api/v1 that require authentication
    private static final String API_V1_PATTERN = "/api/v1/**";

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3001"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "Pragma", "Expires"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                        "default-src 'self'; " +
                                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                                "style-src 'self' 'unsafe-inline'; " +
                                                "img-src 'self' data:; " +
                                                "font-src 'self' https://fonts.gstatic.com;"
                                ))
//                                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31536000)) // HSTS (for HTTPS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                )
                .authorizeHttpRequests(req ->
                        req.requestMatchers(WHITE_LIST_URL).permitAll()    // Whitelisted paths
                                .requestMatchers("/scrapers/**").permitAll()
                                .requestMatchers("/scraper-monitor").permitAll()
                                .requestMatchers("/actuator/**").permitAll()

                                .requestMatchers(API_V1_PATTERN).authenticated()  // /api/v1/** paths need authentication
                                .anyRequest().authenticated() // ALLOW ALL or AUTHENTICATED
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        ;

        return http.build();
    }

}