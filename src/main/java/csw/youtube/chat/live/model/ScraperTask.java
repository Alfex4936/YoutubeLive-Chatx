package csw.youtube.chat.live.model;

import com.github.pemistahl.lingua.api.Language;

import java.util.Set;

public record ScraperTask(String videoId, Set<Language> skipLangs) {
}
