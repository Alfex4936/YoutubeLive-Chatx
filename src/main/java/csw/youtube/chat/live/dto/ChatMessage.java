package csw.youtube.chat.live.dto;

public record ChatMessage(
        String videoTitle, String channelName,
        String videoId, String messageId, String author, String message, long timestamp) {
}
