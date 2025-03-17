package csw.youtube.chat.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record UserDto(
        @Schema(description = "사용자 식별자", example = "1")
        Long id,

        @Schema(description = "사용자 이름", example = "user123")
        String username,

        @Schema(description = "사용자 이메일 주소", example = "user@example.com")
        String email
) {
}