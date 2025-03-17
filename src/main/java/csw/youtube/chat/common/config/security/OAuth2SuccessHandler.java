package csw.youtube.chat.common.config.security;

import csw.youtube.chat.user.model.User;
import csw.youtube.chat.user.model.permission.Role;
import csw.youtube.chat.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository; // to fetch userId or extra user info

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration; // e.g., 86400 (1 day)

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        // Find existing user by email
        Optional<User> existingUserOpt = userRepository.findByEmail(email);

        User user;
        if (existingUserOpt.isPresent()) {
            // Update existing user with relevant OAuth2 fields (profile, name, etc.)
            user = existingUserOpt.get();

            String newName = oauth2User.getAttribute("name");
            if (newName != null && !newName.equals(user.getUsername())) {
                user.setUsername(newName);
            }

            String newProfilePic = extractProfilePicture(oauth2User);
            if (newProfilePic != null && !newProfilePic.equals(user.getProfilePictureUrl())) {
                user.setProfilePictureUrl(newProfilePic);
            }

            // Because JPA’s dirty checking is typically enough
            userRepository.save(user);

        } else {
            // No user found; create one from OAuth
            user = createUserFromOAuth(oauth2User);
            userRepository.save(user);
        }

        // Generate token for the user
        String token = generateToken(user);

        // Redirect with JWT
        response.sendRedirect("http://localhost:3001/mypage?token=" + token);
    }

    private String generateToken(User user) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtExpiration))
                .claim("userId", user.getId())
                .claim("roles", user.getRole().name())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private User createUserFromOAuth(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email"); // Google/Naver

        // Extract profile picture based on provider
        String profilePicture = extractProfilePicture(oauth2User);

        return User.builder()
                .username(oauth2User.getAttribute("name")) // Username from OAuth
                .email(email) // Email from OAuth
                .profilePictureUrl(profilePicture) // Store profile picture URL
                .password("") // No password for OAuth users
                .role(Role.USER)
                .build();
    }

    private String extractProfilePicture(OAuth2User oauth2User) {
        String profilePicture = oauth2User.getAttribute("picture"); // Google
        if (profilePicture == null && oauth2User.getAttribute("kakao_account") != null) {
            Map<String, Object> kakaoAccount = oauth2User.getAttribute("kakao_account");
            if (kakaoAccount != null && kakaoAccount.get("profile") != null) {
                profilePicture = kakaoAccount.get("profile").toString();
            }
        }
        if (profilePicture == null && oauth2User.getAttribute("response") != null) {
            Map<String, Object> naverResponse = oauth2User.getAttribute("response");
            if (naverResponse != null && naverResponse.get("profile_image") != null) {
                profilePicture = naverResponse.get("profile_image").toString();
            }
        }
        return profilePicture;
    }

}
