package csw.youtube.chat.live.dto;

import java.util.List;

public record MessagesRequest(String videoId, List<SimpleChatMessage> messages) {
}
