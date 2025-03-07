package csw.youtube.chat.live.service;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import csw.youtube.chat.live.dto.KeywordRankingPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KeywordRankingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private LanguageDetector globalLanguageDetector;

    private KeywordRankingService service;

    @BeforeEach
    public void setup() {

        // Create the service with mocked dependencies.
        service = new KeywordRankingService(redisTemplate, globalLanguageDetector);
        // Call initIgnoreKeywords so that the ignoreKeywords set gets populated.
        // To simulate a file existing in the classpath, we can create a temporary file in the target test-classes directory.
        try {
            File tempFile = File.createTempFile("ignorekeywords", ".txt");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write("tempIgnore\nanotherIgnore");
            }
            // Override the ClassPathResource lookup via Reflection (if needed) or simply ignore since the file may not be found.
            // For our tests, even if the file isn't loaded, the manual additions will be added.
        } catch (IOException e) {
            // ignore in test
        }
        service.initIgnoreKeywords();
    }

    // --- Tests for static helper methods ---
    @Test
    void testIsNumeric() {
        assertTrue(KeywordRankingService.isNumeric("123"));
        assertTrue(KeywordRankingService.isNumeric("12.3"));
        assertTrue(KeywordRankingService.isNumeric("-33"));
        assertTrue(KeywordRankingService.isNumeric("-33.3"));
        assertFalse(KeywordRankingService.isNumeric("abc"));
        assertFalse(KeywordRankingService.isNumeric("12a"));
        assertFalse(KeywordRankingService.isNumeric(""));
        assertFalse(KeywordRankingService.isNumeric("-"));
    }

    @Test
    void testIsSymbolOnly() {
        assertTrue(KeywordRankingService.isSymbolOnly("???"));
        assertTrue(KeywordRankingService.isSymbolOnly("..."));
        assertFalse(KeywordRankingService.isSymbolOnly("a?"));
        assertFalse(KeywordRankingService.isSymbolOnly("hello"));
    }

    // --- Test initIgnoreKeywords (using reflection to check the field) ---
    @Test
    void testInitIgnoreKeywords() {
        // Use reflection to get the ignoreKeywords field.
        @SuppressWarnings("unchecked") Set<String> ignoreSet = (Set<String>) ReflectionTestUtils.getField(service, "ignoreKeywords");
        // We expect that it has been populated either from the file or from the manual additions.
        assertNotNull(ignoreSet);
        assertFalse(ignoreSet.isEmpty());
    }

    // --- Tests for updateKeywordRanking ---

    @Test
    void testUpdateKeywordRanking_NullMessage() {
        service.updateKeywordRanking("video1", null, Collections.emptySet());
        verifyNoInteractions(zSetOperations);
    }

    @Test
    void testUpdateKeywordRanking_ShortMessage() {
        service.updateKeywordRanking("video1", "hi", Collections.emptySet());
        verifyNoInteractions(zSetOperations);
    }

    @Test
    void testUpdateKeywordRanking_SkipLanguage() {
        // Simulate that the detected language is ENGLISH.
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.ENGLISH);

        // Create a skip set that contains ENGLISH.
        Set<Language> skipLangs = Collections.singleton(Language.ENGLISH);

        // Call the method; since detected language is in skipLangs, no redis interactions should occur.
        service.updateKeywordRanking("video1", "hello world", skipLangs);

        verifyNoInteractions(zSetOperations);
    }

    @Test
    void testUpdateKeywordRanking_WordTooShort() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // "hello hi" should process "hello" (valid, 5 letters) and skip "hi" (only 2 letters).
        service.updateKeywordRanking("video1", "hello hi", Collections.emptySet());

        // Verify that "hello" is processed.
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);

        // Verify that expire is set.
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_SkipsNumericWord() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // "hello 123" should process "hello" and skip "123"
        service.updateKeywordRanking("video1", "hello 123", Collections.emptySet());
        // Verify that incrementScore is called for "hello" only.
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);
        // Also verify that expire is set.
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_SymbolOnlyWord() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // "!!!" is symbol-only so it will be skipped while "hello" is processed.
        service.updateKeywordRanking("video1", "hello !!!", Collections.emptySet());
        // Expect incrementScore to be called for "hello" only.
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);
        // Simulate size call.
        // when(zSetOperations.size("video:video1:keywords")).thenReturn(1L);
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_RepeatedWord() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // "aaaaa" is a repeated character word, so only "hello" should be processed.
        service.updateKeywordRanking("video1", "hello aaaaa", Collections.emptySet());
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_OnlyVowels() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // "ㅏㅏㅏ" should be skipped as it consists only of vowels in the specified range.
        service.updateKeywordRanking("video1", "hello ㅏㅏㅏ", Collections.emptySet());
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_ValidMessage() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // A valid message with two words: "Hello World"
        service.updateKeywordRanking("video1", "Hello World", Collections.emptySet());
        // Expect both words to be processed (converted to lowercase)
        verify(zSetOperations).incrementScore("video:video1:keywords", "hello", 1.0);
        verify(zSetOperations).incrementScore("video:video1:keywords", "world", 1.0);
        // Simulate size call for trimming branch (size less than MAX_KEYWORDS)
        // when(zSetOperations.size("video:video1:keywords")).thenReturn(50L);
        verify(redisTemplate).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    @Test
    void testUpdateKeywordRanking_Trimming() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(globalLanguageDetector.detectLanguageOf(anyString())).thenReturn(Language.FRENCH);
        // Valid message; trimming branch will be executed if size > MAX_KEYWORDS.
        service.updateKeywordRanking("video1", "Hello World", Collections.emptySet());
        // Simulate that the size is greater than MAX_KEYWORDS (200)
        when(zSetOperations.size("video:video1:keywords")).thenReturn(250L);
        // Call update again to trigger the trimming branch.
        service.updateKeywordRanking("video1", "Hello World", Collections.emptySet());
        // Verify that removeRange is called with the correct parameters.
        verify(zSetOperations, atLeastOnce()).removeRange("video:video1:keywords", 0, 250L - 200 - 1);
        verify(redisTemplate, atLeastOnce()).expire("video:video1:keywords", 60L, TimeUnit.MINUTES);
    }

    // --- Tests for getter methods ---

    @Test
    void testGetTopKeywords() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        String key = "video:video1:keywords";
        Set<ZSetOperations.TypedTuple<String>> fakeSet = new HashSet<>();
        // Create a fake typed tuple.
        ZSetOperations.TypedTuple<String> tuple = new ZSetOperations.TypedTuple<>() {
            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return 0;
            }

            @Override
            public String getValue() {
                return "hello";
            }

            @Override
            public Double getScore() {
                return 1.0;
            }
        };
        fakeSet.add(tuple);
        when(zSetOperations.reverseRangeWithScores(key, 0, 0)).thenReturn(fakeSet);

        Set<ZSetOperations.TypedTuple<String>> result = service.getTopKeywords("video1", 1);
        assertEquals(fakeSet, result);
    }

    @Test
    void testGetTopKeywordStrings() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        String key = "video:video1:keywords";
        Set<ZSetOperations.TypedTuple<String>> fakeSet = new HashSet<>();
        ZSetOperations.TypedTuple<String> tuple = new ZSetOperations.TypedTuple<>() {
            @Override
            public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return 0;
            }

            @Override
            public String getValue() {
                return "hello";
            }

            @Override
            public Double getScore() {
                return 1.0;
            }
        };
        fakeSet.add(tuple);
        when(zSetOperations.reverseRangeWithScores(key, 0, 0)).thenReturn(fakeSet);

        List<KeywordRankingPair> result = service.getTopKeywordStrings("video1", 1);
        assertEquals(1, result.size());
        assertEquals("hello", result.getFirst().keyword());
        assertEquals(1.0, result.getFirst().score());
    }
}
