package csw.youtube.chat.live.service;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.common.util.LocalDater;
import csw.youtube.chat.live.dto.MetricsUpdateRequest;
import csw.youtube.chat.live.model.ScraperState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static csw.youtube.chat.common.config.LinguaConfig.parseLanguages;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final YTRustScraperService scraperService;

    public void updateStateFields(ScraperState state, MetricsUpdateRequest request) {
        ScraperState.Status newStatus = ScraperState.Status.valueOf(request.status());
        state.setStatus(newStatus);

        if (newStatus == ScraperState.Status.COMPLETED) {
            state.setFinishedAt(Instant.now());
        }

        long lastThroughput = request.messagesInLastInterval();
        state.setLastThroughput(lastThroughput);
        state.getTotalMessages().set(request.totalMessages());

        long intervals = state.getIntervalsCount().incrementAndGet();
        double newAvg = intervals == 1
                ? lastThroughput
                : (state.getAverageThroughput() * (intervals - 1) + lastThroughput) / intervals;

        state.setAverageThroughput(newAvg);
        state.setMaxThroughput(Math.max(lastThroughput, state.getMaxThroughput()));
    }

    public void updateMetadata(ScraperState state, MetricsUpdateRequest request) {
        setIfNotEmpty(request.topChatters(), state::setTopChatters);
        setIfNotEmpty(request.recentDonations(), state::setRecentDonations);
        setIfNotEmpty(request.reason(), state::setReason);

        if (state.getStatus() == ScraperState.Status.IDLE && request.skipLangs() != null) {
            Set<Language> skipLangs = parseLanguages(request.skipLangs()
                    .subList(0, Math.min(5, request.skipLangs().size())));
            state.setSkipLangs(skipLangs);
        }

        if (request.createdAt() != null) {
            state.setCreatedAt(request.createdAt());
        }

        // Handle optional updates for title and channel
        if (List.of(ScraperState.Status.IDLE, ScraperState.Status.RUNNING).contains(state.getStatus())) {
            setIfNotEmpty(request.videoTitle(), state::setVideoTitle);
            setIfNotEmpty(request.channelName(), state::setChannelName);
        }
    }

    // Generic helper to set values if they are not empty
    private <T> void setIfNotEmpty(T value, Consumer<T> setter) {
        if (value != null && (!(value instanceof Collection) || !((Collection<?>) value).isEmpty())) {
            setter.accept(value);
        }
    }


    public TimeSeriesCollection buildTimeSeries(String videoId) {
        Map<Instant, Integer> messageCounts = scraperService.getMessageCounts(videoId);
        TimeSeries series = new TimeSeries("Messages");

        messageCounts.forEach((instant, count) ->
                series.addOrUpdate(new Second(Date.from(instant)), count)
        );

        return new TimeSeriesCollection(series);
    }

    public JFreeChart createChart(ScraperState state, TimeSeriesCollection dataset, String lang) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                state.getVideoTitle(), "Time", "Message Count", dataset,
                false, true, false);

        chart.setTitle(new TextTitle(state.getVideoTitle(), new Font("SansSerif", Font.BOLD, 14)));

        Locale locale = LocalDater.getLocaleFromLang(lang);
        String formattedDate = LocalDater.getLocalizedDate(locale);
        String videoUrl = state.getVideoUrl();

        chart.addSubtitle(new TextTitle(formattedDate + " | " + videoUrl, new Font("SansSerif", Font.ITALIC, 12)));

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setBackgroundPaint(new Color(240, 240, 240));

        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss", locale));
        domainAxis.setVerticalTickLabels(true);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));

        plot.setRenderer(renderer);

        return chart;
    }
}