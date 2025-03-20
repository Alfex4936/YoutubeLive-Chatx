package csw.youtube.chat.live.controller;

import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.dto.MessagesRequest;
import csw.youtube.chat.live.dto.MetricsUpdateRequest;
import csw.youtube.chat.live.dto.ScraperMetrics;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.RankingService;
import csw.youtube.chat.live.service.StatisticsService;
import csw.youtube.chat.live.service.YTRustScraperService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/scrapers")
public class StatisticsController {

    private final YTRustScraperService scraperService;
    private final RankingService rankingService;
    private final StatisticsService statisticsService;

    @PatchMapping("/updateMetrics")
    public ResponseEntity<Void> updateMetrics(@RequestBody MetricsUpdateRequest request) {
        ScraperState state = scraperService.getScraperStates()
                .computeIfAbsent(request.videoId(), ScraperState::new);

        statisticsService.updateStateFields(state, request);
        statisticsService.updateMetadata(state, request);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> processMessages(@RequestBody MessagesRequest request) {
        scraperService.processChatMessages(request.videoId(), request.messages());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/messageGraph")
    public void getMessageGraph(@RequestParam String videoId,
                                @RequestParam String lang,
                                HttpServletResponse response) throws IOException {
        ScraperState state = scraperService.fetchScraperState(videoId);
        if (state == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        TimeSeriesCollection dataset = statisticsService.buildTimeSeries(videoId);
        JFreeChart chart = statisticsService.createChart(state, dataset, lang);

        response.setContentType("image/png");
        ChartUtils.writeChartAsPNG(response.getOutputStream(), chart, 1000, 700);
    }

    @GetMapping("/statistics")
    public ResponseEntity<ScraperMetrics> getScraperStat(@RequestParam String videoId) {
        ScraperState state = scraperService.getScraperState(videoId);

        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Double> topLanguages = rankingService.getTopLanguages(videoId, 3);
        List<KeywordRankingPair> topKeywordsWithScores = rankingService.getTopKeywordStrings(videoId, 5);

        long runningTimeMinutes = 0;
        if (state.getStatus().name().equals("RUNNING")) {
            Instant createdAt = state.getCreatedAt();
            if (createdAt != null) {
                runningTimeMinutes = Duration.between(createdAt, Instant.now()).toMinutes();
            }
        }

        ScraperMetrics metrics = new ScraperMetrics(
                state.getVideoTitle(),
                state.getChannelName(),
                state.getVideoUrl(),
                state.getStatus(),
                runningTimeMinutes,
                state.getSkipLangs(),
                state.getTopChatters(),
                state.getRecentDonations(),
                state.getLastThroughput(),
                state.getMaxThroughput(),
                state.getAverageThroughput(),
                state.getTotalMessages().get(),
                topKeywordsWithScores,
                topLanguages,
                state.getThreadName(),
                state.getCreatedAt(),
                state.getFinishedAt(),
                state.getReason());

        return ResponseEntity.ok(metrics);
    }
}