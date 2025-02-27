package csw.youtube.chat.profanity.service;

import csw.youtube.chat.profanity.entity.ProfanityLog;
import csw.youtube.chat.profanity.repository.ProfanityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfanityLogService {

    private final ProfanityLogRepository repository;
    private final ProfanityCheckService profanityCheckService; // Your existing service

    /**
     * Checks if the message contains profanity.
     * If so, stores a log with the username, message, and current timestamp.
     */
    @Async("chatScraperExecutor")
    public void logIfProfane(String username, String message) {
        if (profanityCheckService.containsProfanity(message)) {
            ProfanityLog logEntry = new ProfanityLog();
            logEntry.setUsername(username);
            logEntry.setMessage(message);
            logEntry.setTimestamp(LocalDateTime.now());
            repository.save(logEntry);
        }
    }
}