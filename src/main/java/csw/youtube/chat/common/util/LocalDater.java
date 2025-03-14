package csw.youtube.chat.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocalDater {
    /**
     * Maps the language code to a Java Locale.
     */
    public static Locale getLocaleFromLang(String lang) {
        Map<String, Locale> localeMap = new HashMap<>();
        localeMap.put("en", Locale.ENGLISH);
        localeMap.put("fr", Locale.FRENCH);
        localeMap.put("ja", Locale.JAPANESE);
        localeMap.put("ko", Locale.KOREAN);
        localeMap.put("de", Locale.GERMAN);
        localeMap.put("es", Locale.forLanguageTag("es"));

        return localeMap.getOrDefault(lang, Locale.ENGLISH);
    }

    /**
     * Formats the date according to the given locale.
     */
    public static String getLocalizedDate(Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getDateFormatPattern(locale), locale)
                .withZone(ZoneId.systemDefault());
        return formatter.format(Instant.now());
    }

    /**
     * Returns a localized date pattern based on the locale.
     */
    public static String getDateFormatPattern(Locale locale) {
        if (Locale.FRENCH.equals(locale)) {
            return "dd MMMM yyyy"; // 14 mars 2025
        } else if (Locale.JAPANESE.equals(locale)) {
            return "yyyy年MM月dd日"; // 2025年3月14日
        } else if (Locale.KOREAN.equals(locale)) {
            return "yyyy년 MM월 dd일"; // 2025년 3월 14일
        } else {
            return "MMMM dd, yyyy"; // Default: March 14, 2025
        }
    }
}
