package csw.youtube.chat.gemini;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRequestDto {
    private List<Content> contents;

    public static GeminiRequestDto fromText(String text) {
        GeminiRequestDto dto = new GeminiRequestDto();
        dto.contents = List.of(new Content(text));
        return dto;
    }

    @Data
    public static class Content {
        private List<Part> parts;

        public Content(String text) {
            parts = new ArrayList<>();
            parts.add(new Part(text));
        }
    }

    public record Part(String text) {
    }
    }