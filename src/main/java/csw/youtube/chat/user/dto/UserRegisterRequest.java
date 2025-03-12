package csw.youtube.chat.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UserRegisterRequest(
        @Schema(description = "회원가입용 사용자 이름", example = "newuser")
        @NotBlank(message = "Username is required")
        String username,

        @Schema(description = "회원가입용 이메일 주소", example = "newuser@example.com")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(description = "회원가입용 비밀번호", example = "s3cur3p@ssw0rd!")
        @NotBlank(message = "Password is required")
        String password
) {
}