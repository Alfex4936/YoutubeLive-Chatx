package csw.youtube.chat.live.service;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordRankingService {

    private static final int MAX_KEYWORDS = 200; // Configurable maximum ranking size
    // Expiration time for each video's keyword ranking key, e.g., 60 minutes
    private static final long EXPIRATION_MINUTES = 60L;


    private final RedisTemplate<String, String> redisTemplate;

    // Define a set of keywords to ignore
    private final Set<String> ignoreKeywords = new HashSet<>();

    /**
     * Loads ignorable keywords from a file and adds extra English and Korean stop words.
     */
    @PostConstruct
    public void initIgnoreKeywords() {
        // Load keywords from ignorekeywords.txt located in the resources folder.
        try (InputStream is = new ClassPathResource("ignorekeywords.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            List<String> fileKeywords = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            ignoreKeywords.addAll(fileKeywords);
            log.info("Loaded {} keywords from ignorekeywords.txt", fileKeywords.size());
        } catch (IOException e) {
            log.error("Failed to load ignorekeywords.txt: {}", e.getMessage(), e);
        }

        // English stop words.
        ignoreKeywords.addAll(List.of(
                "a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z",
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
                "me", "my", "myself", "we", "our", "ours", "ourselves", "se",
                "you", "your", "yours", "yourself", "yourselves",
                "he", "him", "his", "himself", "she", "her", "hers", "herself",
                "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
                "what", "which", "who", "whom", "this", "that", "these", "those",
                "am", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "having", "do", "does", "did", "doing",
                "an", "the",
                "and", "but", "if", "or", "because", "as", "until", "while",
                "of", "at", "by", "for", "with", "about", "against", "between",
                "into", "through", "during", "before", "after", "above", "below",
                "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
                "again", "further", "then", "once", "here", "there", "when", "where",
                "why", "how", "all", "any", "both", "each", "few", "more", "most",
                "other", "some", "such", "no", "nor", "not", "only", "own", "same",
                "so", "than", "too", "very", "s", "t", "can", "will", "just", "don",
                "should", "now", "?", "u", "it's", "they're", "you're", "u're", "ur",
                "yours", "yours'", "don't", "dont", "~", "...", ".", "yea", "yeah", "ok", "okie", "okay"
        ));

        // Korean stop words.
        ignoreKeywords.addAll(List.of(
                "ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
                "ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅒ", "ㅔ", "ㅖ", "ㅘ", "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ",
                "이", "그", "저", "것", "수", "들", "등", "에서", "에게", "으로",
                "하다", "이다", "입니다", "있다", "없다", "그리고", "하지만", "그러나",
                "때문", "그래서", "만약", "만", "뿐", "의", "를", "은", "는", "이야",
                "아니", "한", "한번", "많이", "모두", "너", "나", "우리", "또한",
                "더", "더욱", "아직", "이미", "정말", "저기", "여기", "그곳", "뭐"
        ));

        log.info("Total ignore keywords count: {}", ignoreKeywords.size());
    }

    /**
     * Processes a chat message to update keyword ranking for a video.
     *
     * @param videoId The video identifier.
     * @param message The chat message text.
     */
    public void updateKeywordRanking(String videoId, String message) {
        if (message.length() < 3) {
            return; // Skip short messages
        }
        if (isNumeric(message)) {
            return; // Skip numeric messages
        }

        String key = "video:" + videoId + ":keywords";
        // Basic tokenization (consider more robust parsing if needed)
        String[] words = message.split("\\s+");
        for (String word : words) {
            String keyword = word.trim().toLowerCase();
            if (keyword.isEmpty() || ignoreKeywords.contains(keyword)) {
                continue;
            }
            // Atomically increment the score for the keyword.
            redisTemplate.opsForZSet().incrementScore(key, keyword, 1);
        }
        // Trim the sorted set to MAX_KEYWORDS to conserve memory.
        Long total = redisTemplate.opsForZSet().size(key);
        if (total != null && total > MAX_KEYWORDS) {
            redisTemplate.opsForZSet().removeRange(key, 0, total - MAX_KEYWORDS - 1);
        }
        // Set an expiration time for the keyword ranking key.
        redisTemplate.expire(key, EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Retrieves the top K keywords for the specified video.
     *
     * @param videoId The video identifier.
     * @param k       The number of top keywords to retrieve.
     * @return A set of keywords with their scores.
     */
    public Set<ZSetOperations.TypedTuple<String>> getTopKeywords(String videoId, int k) {
        String key = "video:" + videoId + ":keywords";
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, k - 1);
    }

    public List<Pair<String, Double>> getTopKeywordStrings(String videoId, int k) {
        String key = "video:" + videoId + ":keywords";
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, k - 1);

        // If null or empty, return an empty list
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Collections.emptyList();
        }

        return typedTuples.stream()
                .map(tuple -> Pair.of(tuple.getValue(), tuple.getScore())) // Extract keyword + score
                .collect(Collectors.toList());
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int len = str.length();
        int i = 0;
        boolean hasDecimal = false;

        // Handle negative sign
        if (str.charAt(i) == '-') {
            i++; // Skip the negative sign
            if (i >= len) return false; // "-" alone is not valid
        }

        // Check remaining characters
        while (i < len) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                // Digit is fine
            } else if (c == '.' && !hasDecimal) {
                hasDecimal = true; // Allow one decimal point
                if (i + 1 >= len) return false; // "." must be followed by a digit
            } else {
                return false; // Invalid character
            }
            i++;
        }

        return true;
    }
}