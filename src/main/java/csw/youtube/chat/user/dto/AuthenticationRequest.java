package csw.youtube.chat.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record AuthenticationRequest(
        @Schema(description = "로그인 사용자 이름", example = "user123")
        String username,

        @Schema(description = "로그인 비밀번호", example = "p@ssw0rd!")
        String password
) {
}