package csw.youtube.chat.live.controller;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.common.annotation.ApiV1;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.dto.MessagesRequest;
import csw.youtube.chat.live.dto.MetricsUpdateRequest;
import csw.youtube.chat.live.dto.ScraperMetrics;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.RankingService;
import csw.youtube.chat.live.service.YTRustScraperService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;

import static csw.youtube.chat.common.config.LinguaConfig.parseLanguages;

@RequiredArgsConstructor
@RestController
@RequestMapping("/scrapers")
public class ScraperController {

    private final YTRustScraperService scraperService;
    private final RankingService rankingService;

    @PatchMapping("/updateMetrics")
    public ResponseEntity<Void> updateMetrics(@RequestBody MetricsUpdateRequest request) {
        ScraperState state = scraperService.getScraperStates()
                .computeIfAbsent(request.videoId(), ScraperState::new);

        ScraperState.Status newStatus = ScraperState.Status.valueOf(request.status());
        state.setStatus(newStatus);

        int lastThroughput = request.messagesInLastInterval();
        state.setLastThroughput(lastThroughput);
        state.getTotalMessages().addAndGet(lastThroughput);
        long intervals = state.getIntervalsCount().incrementAndGet();
        double newAvg = intervals == 1
                ? lastThroughput
                : (state.getAverageThroughput() * (intervals - 1) + lastThroughput) / intervals;

        state.setAverageThroughput(newAvg);
        state.setMaxThroughput(Math.max(lastThroughput, state.getMaxThroughput()));

        // Store top chatters if provided
        if (request.topChatters() != null && !request.topChatters().isEmpty()) {
            state.setTopChatters(request.topChatters());
        }

        if (newStatus == ScraperState.Status.IDLE && request.skipLangs() != null) {
            Set<Language> skipLangs = parseLanguages(
                    request.skipLangs().subList(0, Math.min(5, request.skipLangs().size()))
            );
            state.setSkipLangs(skipLangs);
        }

        // TODO what if user updates video title
        if ((newStatus == ScraperState.Status.IDLE || newStatus == ScraperState.Status.RUNNING)) {
            if (request.videoTitle() != null) {
                state.setVideoTitle(request.videoTitle());
            }
            if (request.channelName() != null) {
                state.setChannelName(request.channelName());
            }
        }
        if (request.createdAt() != null) {
            state.setCreatedAt(request.createdAt());
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> processMessages(@RequestBody MessagesRequest request) {
        scraperService.processChatMessages(request.videoId(), request.messages());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/messageGraph")
    public void getMessageGraph(@RequestParam String videoId, HttpServletResponse response) throws IOException {
        ScraperState state = scraperService.getScraperState(videoId);
        if (state == null || !state.isActiveOrDead()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Retrieve message count data
        Map<Instant, Integer> messageCounts = scraperService.getMessageCounts(videoId);
        TimeSeries series = new TimeSeries("Messages");

        // Populate the time series dataset
        for (Map.Entry<Instant, Integer> entry : messageCounts.entrySet()) {
            series.addOrUpdate(new Second(Date.from(entry.getKey())), entry.getValue());
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(series);

        // Create the chart
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                state.getVideoTitle(),
                "Time",
                "Message Count",
                dataset,
                false,  // Legend
                true,   // Tooltips
                false   // URLs
        );

        // Set a proper font for the title (avoid system fallback)
        Font titleFont = new Font("SansSerif", Font.BOLD, 14);
        chart.setTitle(new TextTitle(state.getVideoTitle(), titleFont));

        // Subtitle for video URL
        String videoUrl = scraperService.getScraperState(videoId).getVideoUrl();
        TextTitle subtitle = new TextTitle(new SimpleDateFormat("MMMM dd, yyyy").format(Date.from(Instant.now())) + " | " + videoUrl, new Font("SansSerif", Font.ITALIC, 12));
        chart.addSubtitle(subtitle);

        // Apply custom styling
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setBackgroundPaint(new Color(240, 240, 240));  // Soft background

        // Set time formatting
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
        domainAxis.setVerticalTickLabels(true); // Rotate for better readability

        // Customize line rendering (thicker line + circular markers)
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));  // Thicker line
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Circular markers
        plot.setRenderer(renderer);

        // Enable tooltips
//        renderer.setDefaultToolTipGenerator((dataset, series, item) -> {
//            Instant timestamp = messageCounts.keySet().toArray(new Instant[0])[item];
//            int count = messageCounts.get(timestamp);
//            return String.format("Time: %s\nMessages: %d\nWatch: %s", timestamp, count, scraperService.getScraperState(videoId).getVideoUrl());
//        });

        // Write the chart as PNG
        response.setContentType("image/png");
        ChartUtils.writeChartAsPNG(response.getOutputStream(), chart, 900, 700);
    }


    @GetMapping("/stop")
    public ResponseEntity<Map<String, String>> stopRustScraper(@RequestParam String videoId) {
        String result = scraperService.stopRustScraper(videoId);

        // Encode the message to be URL-safe
        // String encodedMsg = URLEncoder.encode(result, StandardCharsets.UTF_8);

        // Return JSON instead of 302 redirect
        Map<String, String> response = new HashMap<>();
        response.put("message", result);

        return ResponseEntity.ok(response); // Return HTTP 200 with JSON message
    }

    @GetMapping("/start")
    public ResponseEntity<Map<String, String>> startScraper(@RequestParam String videoId,
                                                            @RequestParam(required = false) List<String> langs) {
        if (langs != null) {
            langs = langs.subList(0, Math.min(5, langs.size()));
        }
        Set<Language> skipLangs = parseLanguages(langs);

        if (videoId.startsWith("http")) {
            videoId = videoId.replace(YTRustScraperService.YOUTUBE_WATCH_URL, "");
        }

        boolean queued = scraperService.startRustScraper(videoId, skipLangs);
        String message = queued ? "Scraper queued for video " + videoId : "Scraper already running/queued for video " + videoId;

        // Return JSON instead of 302 redirect
        Map<String, String> response = new HashMap<>();
        response.put("message", message);

        return ResponseEntity.ok(response); // Return HTTP 200 with JSON message
    }


    @GetMapping("/keyword-ranking")
    public java.util.Set<ZSetOperations.TypedTuple<String>> getKeywords(@RequestParam String videoId, @RequestParam int k) {
        return rankingService.getTopKeywords(videoId, k);
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
                state.getLastThroughput(),
                state.getMaxThroughput(),
                state.getAverageThroughput(),
                state.getTotalMessages().get(),
                topKeywordsWithScores,
                topLanguages,
                state.getThreadName(),
                state.getCreatedAt(),
                state.getErrorMessage()
        );

        return ResponseEntity.ok(metrics);
    }
}