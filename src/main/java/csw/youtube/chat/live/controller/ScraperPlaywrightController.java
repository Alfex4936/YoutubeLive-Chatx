package csw.youtube.chat.live.controller;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.live.service.YTRustScraperService;
import csw.youtube.chat.live.service.YTPlaywrightScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static csw.youtube.chat.common.config.LinguaConfig.parseLanguages;

@RequiredArgsConstructor
@RestController
@RequestMapping("/scrapers/playwright")
public class ScraperPlaywrightController {

    private final YTPlaywrightScraperService ytPlaywrightScraperService;

    /**
     * Starts the scraper for the given videoId.
     * Example: GET /start-scraper?videoId=abcd1234
     */
    @GetMapping("/start-scraper")
    public ResponseEntity<Void> startScraper(
            @RequestParam String videoId,
            @RequestParam(required = false) List<String> langs // e.g. ["ENGLISH","SPANISH","FRENCH"]
    ) {
        // Keep max 5
        if (langs != null && langs.size() > 5) {
            langs = langs.subList(0, 5);
        }
        // Convert to a skip-set of Language
        Set<Language> skipLangs = parseLanguages(langs);

        if (videoId.startsWith("http")) {
            videoId = videoId.replace(YTRustScraperService.YOUTUBE_WATCH_URL, "");
        }

        ytPlaywrightScraperService.scrapeChannel(videoId, skipLangs);
//        scraperService.scrapeChannelPool(videoId, skipLangs);
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
        String result = ytPlaywrightScraperService.stopScraper(videoId);

        // Encode the message to be URL-safe
        String encodedMsg = URLEncoder.encode(result, StandardCharsets.UTF_8);

        // Set the Location header for redirection
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/scraper-monitor?message=" + encodedMsg);

        // Return HTTP 302 (FOUND) Redirect Response
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
