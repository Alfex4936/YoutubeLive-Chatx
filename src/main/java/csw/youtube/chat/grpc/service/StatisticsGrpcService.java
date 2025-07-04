package csw.youtube.chat.grpc.service;

import csw.youtube.chat.grpc.statistics.StatisticsServiceGrpc;
import csw.youtube.chat.grpc.statistics.StatisticsServiceProto.*;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import csw.youtube.chat.live.dto.MetricsUpdateRequest;
import csw.youtube.chat.live.dto.MessagesRequest;
import csw.youtube.chat.live.dto.ScraperMetrics;
import csw.youtube.chat.live.model.ChatMessage;
import csw.youtube.chat.live.model.RecentDonator;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.RankingService;
import csw.youtube.chat.live.service.StatisticsService;
import csw.youtube.chat.live.service.YTRustScraperService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeriesCollection;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class StatisticsGrpcService extends StatisticsServiceGrpc.StatisticsServiceImplBase {

    private final YTRustScraperService scraperService;
    private final RankingService rankingService;
    private final StatisticsService statisticsService;

    @Override
    public void getStatistics(GetStatisticsRequest request, StreamObserver<GetStatisticsResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            log.info("gRPC: Getting statistics for video: {}", videoId);

            ScraperState state = scraperService.getScraperState(videoId);

            if (state == null) {
                responseObserver.onError(new RuntimeException("No scraper found for video: " + videoId));
                return;
            }

            // Calculate running time
            long runningTimeMinutes = 0;
            if (state.getCreatedAt() != null) {
                Instant createdAt = state.getCreatedAt();
                Instant endTime = state.getFinishedAt() != null ? state.getFinishedAt() : Instant.now();
                runningTimeMinutes = Duration.between(createdAt, endTime).toMinutes();
            }

            // Get top keywords
            List<KeywordRanking> topKeywords = rankingService.getTopKeywords(videoId, 10).stream()
                    .map(pair -> KeywordRanking.newBuilder()
                            .setKeyword(pair.keyword())
                            .setCount(pair.count())
                            .build())
                    .collect(Collectors.toList());

            // Get language statistics
            List<LanguageStats> languageStats = rankingService.getTopLanguagePercentage(videoId, 3).entrySet().stream()
                    .map(entry -> LanguageStats.newBuilder()
                            .setLanguage(entry.getKey())
                            .setPercentage(entry.getValue())
                            .build())
                    .collect(Collectors.toList());

            // Get top chatters
            List<TopChatter> topChatters = state.getTopChatters().entrySet().stream()
                    .map(entry -> TopChatter.newBuilder()
                            .setUsername(entry.getKey())
                            .setMessageCount(entry.getValue())
                            .build())
                    .collect(Collectors.toList());

            // Get recent donations
            List<RecentDonation> recentDonations = state.getRecentDonations().stream()
                    .map(donator -> RecentDonation.newBuilder()
                            .setUsername(donator.getUsername())
                            .setAmount(donator.getAmount())
                            .setCurrency(donator.getCurrency())
                            .setTimestamp(donator.getTimestamp().getEpochSecond())
                            .build())
                    .collect(Collectors.toList());

            GetStatisticsResponse.Builder responseBuilder = GetStatisticsResponse.newBuilder()
                    .setVideoTitle(state.getVideoTitle() != null ? state.getVideoTitle() : "")
                    .setChannelName(state.getChannelName() != null ? state.getChannelName() : "")
                    .setVideoUrl(state.getVideoUrl() != null ? state.getVideoUrl() : "")
                    .setStatus(state.getStatus().name())
                    .setRunningTimeMinutes(runningTimeMinutes)
                    .addAllSkipLanguages(state.getSkipLangs().stream().map(Enum::name).collect(Collectors.toList()))
                    .addAllTopChatters(topChatters)
                    .addAllRecentDonations(recentDonations)
                    .setLastThroughput(state.getLastThroughput())
                    .setMaxThroughput(state.getMaxThroughput())
                    .setAverageThroughput(state.getAverageThroughput())
                    .setTotalMessages(state.getTotalMessages().get())
                    .addAllTopKeywords(topKeywords)
                    .addAllLanguageStats(languageStats)
                    .setThreadName(state.getThreadName() != null ? state.getThreadName() : "");

            if (state.getCreatedAt() != null) {
                responseBuilder.setCreatedAt(state.getCreatedAt().getEpochSecond());
            }

            if (state.getFinishedAt() != null) {
                responseBuilder.setFinishedAt(state.getFinishedAt().getEpochSecond());
            }

            if (state.getReason() != null) {
                responseBuilder.setReason(state.getReason());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error getting statistics", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateMetrics(UpdateMetricsRequest request, StreamObserver<UpdateMetricsResponse> responseObserver) {
        try {
            log.info("gRPC: Updating metrics for video: {}", request.getVideoId());

            ScraperState state = scraperService.getScraperStates()
                    .computeIfAbsent(request.getVideoId(), ScraperState::new);

            // Convert gRPC request to internal DTO
            csw.youtube.chat.live.dto.MetricsUpdateRequest internalRequest = 
                    new csw.youtube.chat.live.dto.MetricsUpdateRequest(
                            request.getVideoId(),
                            request.getStatus(),
                            request.getThroughput(),
                            request.getTotalMessages(),
                            request.getVideoTitle(),
                            request.getChannelName(),
                            request.getVideoUrl(),
                            request.getReason()
                    );

            statisticsService.updateStateFields(state, internalRequest);
            statisticsService.updateMetadata(state, internalRequest);

            UpdateMetricsResponse response = UpdateMetricsResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Metrics updated successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error updating metrics", e);

            UpdateMetricsResponse response = UpdateMetricsResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error updating metrics: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void processMessages(ProcessMessagesRequest request, StreamObserver<ProcessMessagesResponse> responseObserver) {
        try {
            log.info("gRPC: Processing {} messages for video: {}", 
                    request.getMessagesCount(), request.getVideoId());

            // Convert gRPC messages to internal format
            List<ChatMessage> messages = request.getMessagesList().stream()
                    .map(grpcMsg -> ChatMessage.builder()
                            .author(grpcMsg.getAuthor())
                            .text(grpcMsg.getText())
                            .timestamp(Instant.ofEpochSecond(grpcMsg.getTimestamp()))
                            .language(grpcMsg.getLanguage())
                            .isDonation(grpcMsg.getIsDonation())
                            .donationAmount(grpcMsg.getDonationAmount().isEmpty() ? null : grpcMsg.getDonationAmount())
                            .build())
                    .collect(Collectors.toList());

            // Use internal messages request
            MessagesRequest internalRequest = new MessagesRequest(request.getVideoId(), messages);
            scraperService.processChatMessages(internalRequest.videoId(), internalRequest.messages());

            ProcessMessagesResponse response = ProcessMessagesResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Messages processed successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error processing messages", e);

            ProcessMessagesResponse response = ProcessMessagesResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error processing messages: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getMessageGraph(GetMessageGraphRequest request, StreamObserver<GetMessageGraphResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            String language = request.getLanguage();
            log.info("gRPC: Getting message graph for video: {} in language: {}", videoId, language);

            ScraperState state = scraperService.fetchScraperState(videoId);
            if (state == null) {
                responseObserver.onError(new RuntimeException("Scraper state not found for video: " + videoId));
                return;
            }

            TimeSeriesCollection dataset = statisticsService.buildTimeSeries(videoId);
            JFreeChart chart = statisticsService.createChart(state, dataset, language);

            // Convert chart to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ChartUtils.writeChartAsPNG(outputStream, chart, 1000, 700);
            byte[] graphData = outputStream.toByteArray();

            GetMessageGraphResponse response = GetMessageGraphResponse.newBuilder()
                    .setGraphData(com.google.protobuf.ByteString.copyFrom(graphData))
                    .setContentType("image/png")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error generating message graph", e);
            responseObserver.onError(e);
        }
    }
}