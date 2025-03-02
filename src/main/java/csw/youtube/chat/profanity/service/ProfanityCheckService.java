package csw.youtube.chat.profanity.service;

import csw.youtube.chat.common.util.DAT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProfanityCheckService {

    private static final String BAD_WORDS_FILE = "/badwords.txt";
    private DAT<String> trie;

    @Getter
    private List<String> badWords;

    @PostConstruct
    public void init() throws IOException {
        log.info("Initializing Profanity Service...");
        this.badWords = loadBadWords();
        this.trie = buildTrie(this.badWords);
        log.info("Profanity Service initialized successfully.");
        assert (trie != null) : "Trie should not be null.";
    }

    private List<String> loadBadWords() throws IOException {
        log.info("Loading bad words from file: {}", BAD_WORDS_FILE);
        ClassPathResource resource = new ClassPathResource(BAD_WORDS_FILE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(resource.getInputStream()), StandardCharsets.UTF_8))) {

            return reader.lines()
                    .filter(word -> !word.trim().isEmpty()) // added trim
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    private DAT<String> buildTrie(List<String> words) {
        log.info("Building Trie with {} bad words.", words.size());
        Map<String, String> badWordsMap = words.stream()
                .collect(Collectors.toMap(word -> word, word -> word, (oldValue, newValue) -> oldValue, TreeMap::new));
        DAT<String> newTrie = new DAT<>();
        newTrie.build(badWordsMap);
        return newTrie;
    }

    public boolean containsProfanity(String text) {
        DAT.Hit<String> hit = trie.findFirst(text);
        return hit != null && hit.begin() >= 0 && hit.end() >= 0;
    }
}
