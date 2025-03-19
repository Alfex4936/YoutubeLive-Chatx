package csw.youtube.chat.gemini;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AISummarizeRequestDto {

    @NotBlank
    private String videoId;

    @NotBlank
    private String lang; // Language: "ko", "en", "ja", "fr"
}