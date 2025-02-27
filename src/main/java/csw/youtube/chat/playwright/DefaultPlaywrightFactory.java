package csw.youtube.chat.playwright;

import com.microsoft.playwright.Playwright;
import org.springframework.stereotype.Component;

@Component
public class DefaultPlaywrightFactory implements PlaywrightFactory {
    @Override
    public Playwright create() {
        return Playwright.create();
    }
}
