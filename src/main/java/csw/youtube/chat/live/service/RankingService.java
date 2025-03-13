package csw.youtube.chat.live.service;


import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final int MAX_KEYWORDS = 200; // Configurable maximum ranking size
    private static final int MAX_LANG_RANKINGS = 3; // top 3 langs
    // Expiration time for each video's keyword ranking key, e.g., minutes
    private static final long EXPIRATION_MINUTES = 120L;


    private final RedisTemplate<String, String> redisTemplate;
    private final LanguageDetector globalLanguageDetector;

    // Define a set of keywords to ignore
    private final Set<String> ignoreKeywords = new HashSet<>();

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int len = str.length();
        int i = 0;
        boolean hasDecimal = false;

        if (str.charAt(i) == '-') {
            i++; // Skip negative sign
            if (i >= len) return false; // "-" alone is not valid
        }

        while (i < len) {
            char c = str.charAt(i);
            if (Character.isDigit(c)) {
                // It's a number
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

    public static boolean isSymbolOnly(String str) {
        return str.chars().noneMatch(Character::isLetterOrDigit);
    }

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
                "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
                "me", "my", "myself", "we", "our", "ours", "ourselves", "se",
                "you", "your", "yours", "yourself", "yourselves",
                "he", "him", "his", "himself", "she", "her", "hers", "herself",
                "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
                "what", "which", "who", "whom", "this", "that", "these", "those",
                "am", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "having", "do", "does", "did", "doing",
                "an", "the", "hi", "yo", "i'm",
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

        // French stop words.
        ignoreKeywords.addAll(List.of(
                "les", "le", "la", "las", "des", "de", "pas", "est"
        ));

        log.info("Total ignore keywords count: {}", ignoreKeywords.size());
    }

    // TODO time filter?
    /*
    double timestamp = System.currentTimeMillis() / 1000.0; // Unix timestamp in seconds
    redisTemplate.opsForZSet().add(key, keyword, timestamp);

    long cutoff = (System.currentTimeMillis() / 1000) - (5 * 60); // Last 5 minutes
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
    */


    /**
     * Processes a chat message to update keyword ranking for a video.
     *
     * @param videoId The video identifier.
     * @param message The chat message text.
     */
    public void updateKeywordRanking(String videoId, String message, Set<Language> skipLangs) {
        if (message == null || message.codePointCount(0, message.length()) < 3) {
            return; // Skip
        }

        // NOTE do I want to skip by words or guess from entire message
        Language detected = globalLanguageDetector.detectLanguageOf(message);
        // If in skip-langs, skip,  detected == Language.UNKNOWN ||
        if (skipLangs.contains(detected)) {
            // log.debug("Lang {}", detected);
            return;
        }

        String key = "video:" + videoId + ":keywords";
        // Basic tokenization (consider more robust parsing if needed)
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.codePointCount(0, word.length()) < 3 || isNumeric(word)) {
                continue;
            }

            if (isSymbolOnly(word)) {
                continue; // Skip if the word is entirely symbols
            }

            if (word.chars().distinct().count() == 1) {
                continue; // Skip words that are made of a single repeated character
            }

            if (word.chars().allMatch(ch ->
                    // (ch >= 'ㄱ' && ch <= 'ㅎ') || // Korean consonants
                            (ch >= 'ㅏ' && ch <= 'ㅣ'))) { // Korean vowels
                continue; // Skip words containing only these characters
            }


            String keyword = word.trim().toLowerCase(); // Locale.Root?
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

    public List<KeywordRankingPair> getTopKeywordStrings(String videoId, int k) {
        String key = "video:" + videoId + ":keywords";
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, k - 1);

        // If null or empty, return an empty list
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Collections.emptyList();
        }

        return typedTuples.stream()
                .map(tuple -> new KeywordRankingPair(tuple.getValue(), tuple.getScore())) // Extract keyword + score
                .collect(Collectors.toList());
    }

    public void updateLanguageStats(String videoId, String message) {
        Language detectedLang = globalLanguageDetector.detectLanguageOf(message);
        if (detectedLang == Language.UNKNOWN) {
            return; // 알 수 없는 언어는 카운트하지 않음
        }

        String key = "video:" + videoId + ":lang-stats";

        // Redis Sorted Set (ZINCRBY) 사용하여 해당 언어 카운트 증가
        redisTemplate.opsForZSet().incrementScore(key, detectedLang.name(), 1);
        // 전체 메시지 개수도 증가 (TOTAL_MESSAGES 키)
        redisTemplate.opsForZSet().incrementScore(key, "TOTAL_MESSAGES", 1);

        Long total = redisTemplate.opsForZSet().size(key);
        if (total != null && total > MAX_KEYWORDS) {
            redisTemplate.opsForZSet().removeRange(key, 0, total - MAX_LANG_RANKINGS - 1);
        }

        // Redis TTL 설정 (기본 2시간 유지)
        redisTemplate.expire(key, EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /*
    📌 KOREAN: 80.5% 🟦
    📌 ENGLISH: 10.2% 🟩
    📌 JAPANESE: 5.3% 🟥
    */
    public Map<String, Double> getTopLanguages(String videoId, int topN) {
        String key = "video:" + videoId + ":lang-stats";

        // 전체 메시지 개수 가져오기
        Double totalMessages = redisTemplate.opsForZSet().score(key, "TOTAL_MESSAGES");
        if (totalMessages == null || totalMessages == 0) {
            return Collections.emptyMap(); // 메시지가 없으면 빈 값 반환
        }

        // 상위 N개 언어 가져오기 (내림차순 정렬)
        Set<ZSetOperations.TypedTuple<String>> topLangs =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

        // 결과 변환 (언어 -> 비율%)
        Map<String, Double> langStats = new LinkedHashMap<>();
        if (topLangs != null) {
            for (ZSetOperations.TypedTuple<String> entry : topLangs) {
                String lang = entry.getValue();
                Double count = entry.getScore();
                if (count == null || count == 0 || "TOTAL_MESSAGES".equals(lang)) {
                    continue; // Skip "TOTAL_MESSAGES"
                }
                // 비율 계산 후 소수점 한 자리까지 반올림
                double percentage = Math.round((count / totalMessages) * 1000.0) / 10.0;
                langStats.put(lang, percentage);
            }
        }
        return langStats;
    }


}