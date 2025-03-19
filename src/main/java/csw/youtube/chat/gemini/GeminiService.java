package csw.youtube.chat.gemini;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api-key}")
    private String apiKey;

    public String summarizeChat(String videoTitle, String recentMessages, String lang) {
        if (recentMessages == null || recentMessages.isBlank()) {
            return "No recent messages available.";
        }

        String url = GEMINI_API_URL + apiKey;

        // Construct input text with context
        String chatText = String.join("\n", recentMessages);
        String prompt = "Summarize in 2~3 sentences simply, the last 5 minutes of chat messages from the video titled '"
                + videoTitle + "' in language " + lang + ":\n" + chatText;

        GeminiRequestDto formattedRequest = GeminiRequestDto.fromText(prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequestDto> entity = new HttpEntity<>(formattedRequest, headers);
        ResponseEntity<GeminiResponseDto> response = restTemplate.exchange(url, HttpMethod.POST, entity, GeminiResponseDto.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            GeminiResponseDto responseDto = response.getBody();
            if (responseDto.getCandidates() != null && !responseDto.getCandidates().isEmpty()) {
                return responseDto.getCandidates().getFirst().getContent().getParts().getFirst().text().strip();
            }
        }
        return "Failed to summarize.";
    }
}