package csw.youtube.chat.user.dto;

import csw.youtube.chat.user.model.User;
import csw.youtube.chat.user.model.permission.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.stream.Collectors;

public record UserProfileResponse(

        @Schema(description = "사용자 식별자", example = "1")
        Long id,

        @Schema(description = "사용자 이름", example = "user123")
        String username,

        @Schema(description = "사용자 이메일 주소", example = "user@example.com")
        String email,

        @Schema(description = "사용자의 프로필 사진 URL", example = "https://example.com/profile.jpg")
        String profilePictureUrl,

        @Schema(description = "사용자의 역할", example = "AdminRole")
        String role,

        @Schema(description = "역할 별 권한 목록", example = "[\"READ\", \"WRITE\", \"DELETE\"]")
        Set<String> permissions,

        @Schema(description = "역할에 대한 설명", example = "System Administrator with full privileges")
        String roleDescription

) {
    public UserProfileResponse(User user) {
        this(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfilePictureUrl(), // Add this field
                user.getRole().name(),
                user.getRole().permissions().stream()
                        .map(Permission::getPermission)
                        .collect(Collectors.toSet()),
                getRoleDescription(user.getRole())
        );
    }

    private static String getRoleDescription(Role role) {
        return switch (role) {
            case AdminRole a -> "System Administrator with full privileges";
            case ManagerRole m -> "Team Manager with resource management access";
            case UserRole u -> "Standard application user";
        };
    }
}
