package csw.youtube.chat.live.service;

import csw.youtube.chat.live.dto.ChatMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts the chat message to all subscribers of "/live/chat/{videoId}".
     */
    public void broadcast(String videoId, ChatMessage chatMessage) {
        messagingTemplate.convertAndSend("/live/chat/" + videoId, chatMessage);
    }
}