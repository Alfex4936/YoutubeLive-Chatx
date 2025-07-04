package csw.youtube.chat.grpc.service;

import csw.youtube.chat.gemini.GeminiService;
import csw.youtube.chat.grpc.ai.AiServiceGrpc;
import csw.youtube.chat.grpc.ai.AiServiceProto.*;
import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.YTRustScraperService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AiGrpcService extends AiServiceGrpc.AiServiceImplBase {

    private final GeminiService geminiService;
    private final YTRustScraperService scraperService;

    @Override
    public void summarizeChat(SummarizeChatRequest request, StreamObserver<SummarizeChatResponse> responseObserver) {
        try {
            String videoId = request.getVideoId();
            String language = request.getLanguage();

            log.info("gRPC: Summarizing chat for video: {} in language: {}", videoId, language);

            ScraperState state = scraperService.getScraperStates().get(videoId);

            if (state == null || state.getStatus() != ScraperState.Status.RUNNING) {
                SummarizeChatResponse response = SummarizeChatResponse.newBuilder()
                        .setSummary("")
                        .setSuccess(false)
                        .setErrorMessage("Scraper is not active for the provided video ID.")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            String recentMessages = state.getCombinedRecentMessages();

            if (recentMessages.isEmpty()) {
                SummarizeChatResponse response = SummarizeChatResponse.newBuilder()
                        .setSummary("No recent messages to summarize.")
                        .setSuccess(true)
                        .setErrorMessage("")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            String summary = geminiService.summarizeChat(state.getVideoTitle(), recentMessages, language);

            SummarizeChatResponse response = SummarizeChatResponse.newBuilder()
                    .setSummary(summary)
                    .setSuccess(true)
                    .setErrorMessage("")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error summarizing chat", e);

            SummarizeChatResponse response = SummarizeChatResponse.newBuilder()
                    .setSummary("")
                    .setSuccess(false)
                    .setErrorMessage("Error occurred while summarizing: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}