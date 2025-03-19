package csw.youtube.chat.live.controller;

import csw.youtube.chat.common.annotation.ApiV1;
import csw.youtube.chat.common.annotation.RateLimit;
import csw.youtube.chat.gemini.AISummarizeRequestDto;
import csw.youtube.chat.gemini.GeminiRequestDto;
import csw.youtube.chat.gemini.GeminiService;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.YTRustScraperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;
import java.util.Map;

@ApiV1
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {
    private final GeminiService geminiService;
    private final YTRustScraperService scraperService;

    @RateLimit(key = "summarize", permitsPerSecond = 1.0 / 60, tolerance = 0, toleranceUnit = ChronoUnit.SECONDS)
    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody @Valid AISummarizeRequestDto requestDto) {
        ScraperState state = scraperService.getScraperStates().get(requestDto.getVideoId());

        if (state == null || state.getStatus() != ScraperState.Status.RUNNING) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Scraper is not active for the provided video ID."
            ));
        }

        String recentMessages = state.getCombinedRecentMessages();

        if (recentMessages.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "summary", "No recent messages to summarize."
            ));
        }

        String summary = geminiService.summarizeChat(state.getVideoTitle(), recentMessages, requestDto.getLang());
        return ResponseEntity.ok(Map.of("summary", summary));
    }
}

