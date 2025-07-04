package csw.youtube.chat.grpc.service;

import com.github.pemistahl.lingua.api.Language;
import csw.youtube.chat.grpc.scraper.ScraperServiceGrpc;
import csw.youtube.chat.grpc.scraper.ScraperServiceProto.*;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.YTRustScraperService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Set;
import java.util.stream.Collectors;

import static csw.youtube.chat.common.config.LinguaConfig.parseLanguages;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScraperGrpcService extends ScraperServiceGrpc.ScraperServiceImplBase {

    private final YTRustScraperService scraperService;

    @Override
    public void startScraper(StartScraperRequest request, StreamObserver<StartScraperResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            Set<Language> skipLangs = parseLanguages(request.getSkipLanguagesList());

            log.info("gRPC: Starting scraper for video: {}", videoId);

            // Get current state for queue position calculation
            int queueSize = scraperService.getQueueSize();
            int activeScrapers = scraperService.getActiveScrapersCount();
            int maxScrapers = scraperService.getMaxConcurrentScrapers();

            boolean success = scraperService.startRustScraper(videoId, skipLangs);

            // Determine queue position
            int position = (success && activeScrapers >= maxScrapers) ? queueSize + 1 : 0;

            String message = success
                    ? "Scraper queued for video " + videoId
                    : "Scraper already running/queued for video " + videoId;

            StartScraperResponse response = StartScraperResponse.newBuilder()
                    .setMessage(message)
                    .setQueuePosition(position)
                    .setSuccess(success)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error starting scraper", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void stopScraper(StopScraperRequest request, StreamObserver<StopScraperResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            log.info("gRPC: Stopping scraper for video: {}", videoId);

            String result = scraperService.stopRustScraper(videoId);

            StopScraperResponse response = StopScraperResponse.newBuilder()
                    .setMessage(result)
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error stopping scraper", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getScraperStatus(GetScraperStatusRequest request, StreamObserver<GetScraperStatusResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            log.info("gRPC: Getting scraper status for video: {}", videoId);

            ScraperState state = scraperService.getScraperState(videoId);

            if (state == null) {
                GetScraperStatusResponse response = GetScraperStatusResponse.newBuilder()
                        .setStatus("NOT_FOUND")
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            GetScraperStatusResponse.Builder responseBuilder = GetScraperStatusResponse.newBuilder()
                    .setStatus(state.getStatus().name())
                    .setVideoTitle(state.getVideoTitle() != null ? state.getVideoTitle() : "")
                    .setChannelName(state.getChannelName() != null ? state.getChannelName() : "")
                    .setTotalMessages(state.getTotalMessages().get())
                    .setLastThroughput(state.getLastThroughput())
                    .setMaxThroughput(state.getMaxThroughput())
                    .setAverageThroughput(state.getAverageThroughput())
                    .addAllSkipLanguages(
                            state.getSkipLangs().stream()
                                    .map(Language::name)
                                    .collect(Collectors.toList())
                    );

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
            log.error("gRPC: Error getting scraper status", e);
            responseObserver.onError(e);
        }
    }
}