package csw.youtube.chat.live.controller;

import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.live.service.ChatMessageService;
import csw.youtube.chat.live.service.KeywordRankingService;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
public class ChatMessageController {

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

    /**
     * Starts the scraper for the given videoId.
     * Example: GET /start-scraper?videoId=abcd1234
     */
    @GetMapping("/start-scraper")
    public ResponseEntity<Void> startScraper(@RequestParam String videoId) {
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
    public ResponseEntity<Void> stopScraper(@RequestParam String videoId) {
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