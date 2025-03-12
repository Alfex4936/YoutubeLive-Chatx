package csw.youtube.chat.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJI...")
        String refreshToken
) {}