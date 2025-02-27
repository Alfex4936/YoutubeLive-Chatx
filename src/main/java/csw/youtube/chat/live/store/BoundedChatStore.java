package csw.youtube.chat.live.store;


import csw.youtube.chat.live.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

// local mem to save chats
@Slf4j
@RequiredArgsConstructor
public class BoundedChatStore {
    private final int maxSize;
    private final long expirationMillis;
    private final Deque<ChatMessage> messages = new LinkedList<>();

    public synchronized void add(ChatMessage message) {
        messages.addLast(message);
        // If we exceed capacity, remove the oldest messages
        while (messages.size() > maxSize) {
            messages.removeFirst();
        }
        purgeOldMessages();
    }

    public synchronized List<ChatMessage> getMessages() {
        purgeOldMessages();
        return new ArrayList<>(messages);
    }

    private void purgeOldMessages() {
        long now = System.currentTimeMillis();
        while (!messages.isEmpty() && now - messages.peekFirst().timestamp() > expirationMillis) {
            ChatMessage removed = messages.removeFirst();
            log.debug("Purged message from {} at {}", removed.author(), removed.timestamp());
        }
    }
}