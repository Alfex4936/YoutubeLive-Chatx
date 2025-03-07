package csw.youtube.chat.common.config;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class LinguaConfig {

    @Bean
    public LanguageDetector globalLanguageDetector() {
        // Build it once at startup
        return LanguageDetectorBuilder
                .fromAllSpokenLanguages()
                // or e.g. fromLanguages(Language.ENGLISH, Language.SPANISH, Language.FRENCH, ...)
                .withPreloadedLanguageModels()  // Eager load all models into memory
                .withLowAccuracyMode()          // Optional: for speed, if short messages are typical
                .withMinimumRelativeDistance(0.00) // So we don't over-filter short texts
                .build();
    }

    public static Set<Language> parseLanguages(List<String> langs) {
        if (langs == null || langs.isEmpty()) {
            return Collections.emptySet(); // skip-langs is empty => don't skip any
        }
        Set<Language> set = new HashSet<>();
        for (String langStr : langs) {
            try {
                Language lang = Language.valueOf(langStr.toUpperCase());
                set.add(lang);
            } catch (IllegalArgumentException e) {
                // ignore invalid lang
            }
        }
        return set;
    }
}
