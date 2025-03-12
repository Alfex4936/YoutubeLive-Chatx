package csw.youtube.chat.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record AuthenticationResponse(
        @Schema(description = "엑세스 토큰", example = "eyJhbGciOiJI...")
        @JsonProperty("access_token")
        String accessToken,

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJI...")
        @JsonProperty("refresh_token")
        String refreshToken
) {}