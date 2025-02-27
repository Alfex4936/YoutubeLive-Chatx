package csw.youtube.chat.live.controller;

import csw.youtube.chat.live.dto.ChatMessage;
import csw.youtube.chat.live.service.ChatMessageService;
import csw.youtube.chat.live.service.KeywordRankingService;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
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
     *
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
    public String startScraper(@RequestParam String videoId) {
        // generalize
        if (videoId.startsWith("http")) {
            videoId = videoId.replace(YTChatScraperService.YOUTUBE_WATCH_URL, "");
        }

        scraperService.scrapeChannel(videoId);
        return "Scraper started for video " + videoId;
    }

    /**
     * Stops the scraper for the given videoId if active.
     * Example: GET /stop-scraper?videoId=test-channel
     */
    @GetMapping("/stop-scraper")
    public String stopScraper(@RequestParam String videoId) {
        return scraperService.stopScraper(videoId);
    }


    @GetMapping("/keyword-ranking")
    public java.util.Set<ZSetOperations.TypedTuple<String>> getKeywords(@RequestParam String videoId, @RequestParam int k) {
        return keywordRankingService.getTopKeywords(videoId, k);
    }
}