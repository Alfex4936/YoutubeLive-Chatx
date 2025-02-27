package csw.youtube.chat.live.service;

import csw.youtube.chat.live.store.BoundedChatStore;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMessageService {
    private final ConcurrentHashMap<String, BoundedChatStore> channelStores = new ConcurrentHashMap<>();

    public BoundedChatStore getStore(String channelId) {
        // For example, max 1000 messages, expire messages older than 5 minutes
        return channelStores.computeIfAbsent(channelId, id -> new BoundedChatStore(1000, 5 * 60 * 1000L));
    }
}