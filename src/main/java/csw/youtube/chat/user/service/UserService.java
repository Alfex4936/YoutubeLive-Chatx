package csw.youtube.chat.user.service;

import csw.youtube.chat.common.exception.ResourceNotFoundException;
import csw.youtube.chat.user.dto.UserProfileResponse;
import csw.youtube.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        Long userId = getCurrentUserId();

        return userRepository.findById(userId)
                .map(UserProfileResponse::new)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // Extract userId from JWT
    private Long getCurrentUserId() {
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return authentication.getToken().getClaim("userId");
    }
}
