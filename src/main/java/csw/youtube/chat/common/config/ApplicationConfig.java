package csw.youtube.chat.common.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import csw.youtube.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

//    @Bean
//    public JwtEncoder jwtEncoder(
//            @Value("${application.security.jwt.secret-key}") String secretKey
//    ) {
//        MacAlgorithm algorithm = MacAlgorithm.HS256;
//
//        SecretKeySpec secretKeySpec =
//                new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm.getName());
//
//        // Wrap it in a Nimbus OctetSequenceKey, and specify the algorithm as HS256
//        OctetSequenceKey jwk = new OctetSequenceKey
//                .Builder(secretKeySpec)
//                .algorithm(JWSAlgorithm.HS256)
//                .build();
//
//        // Build a JWKSet containing just that JWK
//        JWKSet jwkSet = new JWKSet(jwk);
//
//        // Provide a JWKSource that returns this single JWKSet
//        JWKSource<SecurityContext> jwkSource = (jwkSelector, _) ->
//                jwkSelector.select(jwkSet);
//
//        // construct the NimbusJwtEncoder from that JWK source
//        return new NimbusJwtEncoder(jwkSource);
//    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${application.security.jwt.secret-key}") String secretKey) {
        MacAlgorithm algorithm = MacAlgorithm.HS256;

        SecretKey key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm.getName());
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${application.security.jwt.secret-key}") String secretKey) {
        MacAlgorithm algorithm = MacAlgorithm.HS256;

        return NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm.getName())
                )
                .macAlgorithm(algorithm)
                .build();
    }

}