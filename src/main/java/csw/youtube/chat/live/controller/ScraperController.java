package csw.youtube.chat.live.controller;

import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.ChatMessageService;
import csw.youtube.chat.live.service.KeywordRankingService;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/scrapers")
public class ScraperController {

    private final ChatMessageService chatMessageService;
    private final YTChatScraperService scraperService;
    private final KeywordRankingService keywordRankingService;

    /**
     * Returns chat messages for a channel that have a timestamp within the last `n` minutes.
     * <p>
     * Example: GET /channel/CHANNEL_ID/messages?n=5
     */
    @GetMapping("/channel/{channelId}/messages")
    public List<ChatMessage> getMessages(
            @PathVariable String channelId,
            @RequestParam(defaultValue = "5") long n) {

        // Retrieve all messages from the bounded store
        List<ChatMessage> allMessages = chatMessageService.getStore(channelId).getMessages();
        long cutoff = System.currentTimeMillis() - (n * 60 * 1000);

        // Filter messages that are newer than the cutoff time
        return allMessages.stream()
                .filter(msg -> msg.timestamp() >= cutoff)
                .collect(Collectors.toList());
    }

    @PostMapping("/{videoId}/start")
    public ResponseEntity<Map<String, String>> startScraper(@PathVariable String videoId) {
        ScraperState state = scraperService.getScraperState(videoId);
        if (state != null && state.getStatus() == ScraperState.Status.RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Scraper already running", "status", "RUNNING"));
        }

        scraperService.scrapeChannel(videoId);
        return ResponseEntity.ok(Map.of("message", "Scraper started", "status", "STARTED"));
    }

    @GetMapping("/{videoId}/status")
    public ResponseEntity<Map<String, Object>> getScraperStatus(@PathVariable String videoId) {
        ScraperState state = scraperService.getScraperState(videoId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Scraper not found", "status", "NOT_FOUND"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", state.getStatus());
        response.put("totalMessages", state.getTotalMessages().get());
        response.put("averageThroughput", state.getAverageThroughput());
        response.put("errorMessage", state.getErrorMessage());  // Allows null values

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{videoId}/stop")
    public ResponseEntity<Map<String, String>> stopScraper(@PathVariable String videoId) {
        String stopped = scraperService.stopScraper(videoId);
        if (stopped.startsWith("Stopping")) {
            return ResponseEntity.ok(Map.of("message", "Scraper stopped", "status", "COMPLETED"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Scraper not running", "status", "NOT_FOUND"));
        }
    }

    /**
     * Starts the scraper for the given videoId.
     * Example: GET /start-scraper?videoId=abcd1234
     */
    @GetMapping("/start-scraper")
    public ResponseEntity<Void> startScraper2(@RequestParam String videoId) {
        if (videoId.startsWith("http")) {
            videoId = videoId.replace(YTChatScraperService.YOUTUBE_WATCH_URL, "");
        }

        scraperService.scrapeChannel(videoId);
        String encodedMsg = URLEncoder.encode("Scraper started for video " + videoId, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/scraper-monitor?message=" + encodedMsg);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);  // 302 redirect
    }


    /**
     * Stops the scraper for the given videoId if active.
     * Example: GET /stop-scraper?videoId=test-channel
     */
    @GetMapping("/stop-scraper")
    public ResponseEntity<Void> stopScraper2(@RequestParam String videoId) {
        String result = scraperService.stopScraper(videoId);

        // Encode the message to be URL-safe
        String encodedMsg = URLEncoder.encode(result, StandardCharsets.UTF_8);

        // Set the Location header for redirection
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/scraper-monitor?message=" + encodedMsg);

        // Return HTTP 302 (FOUND) Redirect Response
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }


    @GetMapping("/keyword-ranking")
    public java.util.Set<ZSetOperations.TypedTuple<String>> getKeywords(@RequestParam String videoId, @RequestParam int k) {
        return keywordRankingService.getTopKeywords(videoId, k);
    }
}