package csw.youtube.chat.common.config.security;

import csw.youtube.chat.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component("securityExpression")
public class SecurityExpression {

    /**
     * Check if the current user is an admin or manager.
     */
    public boolean isAdminOrManager(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        User principalUser = (User) authentication.getPrincipal();
        String roleName = principalUser.getRole().name();
        return "ADMIN".equals(roleName) || "MANAGER".equals(roleName);
    }

}
