package csw.youtube.chat.live.controller;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.service.YTRustScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static csw.youtube.chat.common.config.LinguaConfig.parseLanguages;

@RequiredArgsConstructor
@RestController
@RequestMapping("/scrapers")
public class ScraperController {

    private final YTRustScraperService scraperService;

    // @RateLimit(key = "start-scraper", permitsPerSecond = 1, tolerance = 1000) //
    // 1 req/sec, 1 sec tolerance
    @GetMapping("/start")
    public ResponseEntity<Map<String, String>> startScraper(
            @RequestParam String videoId,
            @RequestParam(required = false) List<String> langs) {

        // Limit languages to 5 if provided
        Set<Language> skipLangs = (langs != null)
                ? parseLanguages(langs.subList(0, Math.min(5, langs.size())))
                : Collections.emptySet();

        videoId = scraperService.sanitizeVideoId(videoId);

        // Get scraper state
        int queueSize = scraperService.getQueueSize();
        int activeScrapers = scraperService.getActiveScrapersCount();
        int maxScrapers = scraperService.getMaxConcurrentScrapers();

        boolean queued = scraperService.startRustScraper(videoId, skipLangs);

        // Determine queue position
        int position = (queued && activeScrapers >= maxScrapers) ? queueSize + 1 : 0;

        return ResponseEntity.ok(Map.of(
                "message", queued
                        ? "Scraper queued for video " + videoId
                        : "Scraper already running/queued for video " + videoId,
                "queuePosition", String.valueOf(position)
        ));
    }

    @GetMapping("/stop")
    public ResponseEntity<Map<String, String>> stopScraper(@RequestParam String videoId) {
        String result = scraperService.stopRustScraper(videoId);
        return ResponseEntity.ok(Collections.singletonMap("message", result));
    }
}