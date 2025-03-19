package csw.youtube.chat.gemini;


import lombok.Data;
import java.util.List;

@Data
public class GeminiResponseDto {
    private List<Candidate> candidates;

    @Data
    public static class Candidate {
        private Content content;
        private String finishReason;
    }

    @Data
    public static class Content {
        private List<Parts> parts;
        private String role;
    }

    public record Parts(String text) {}
}