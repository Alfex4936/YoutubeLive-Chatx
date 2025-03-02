package csw.youtube.chat.live.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatMessageExtractorTest {

    private static Playwright playwright;
    private static Browser browser;
    private static Page page;
    private static final String YOUTUBE_CHAT_URL = "https://www.youtube.com/watch?v=LNtw5Ham47k";

    @BeforeAll
    static void setUp() throws InterruptedException {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(true) // Run headless in production
                        .setArgs(List.of(
                                "--no-sandbox", // Required for Docker/non-root user
                                "--disable-extensions", // Disable extensions for performance
                                "--disable-gpu", // Disable GPU acceleration if not needed
                                "--disable-dev-shm-usage", //  May help with memory issues in containers
                                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36" // Mimic a real browser
                        ))
        );

        page = browser.newPage();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        System.out.println("Opening YouTube Live Chat...");
        page.navigate(YOUTUBE_CHAT_URL);
        page.waitForSelector("iframe#chatframe", new Page.WaitForSelectorOptions().setTimeout(10000));

        // Wait for chat messages to load
        System.out.println("Chat loaded successfully!");
    }

    @AfterAll
    static void tearDown() {
        browser.close();
        playwright.close();
    }

    @Test
    void testExtractSingleUsername() {
        FrameLocator chatFrameLocator = page.frameLocator("iframe#chatframe");
        Locator chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");
        String username = extractUsername(chatMessagesLocator.first());
        assertNotNull(username);
        System.out.println("Username: " + username);
    }

    @Test
    void testExtractSingleMessage() {
        FrameLocator chatFrameLocator = page.frameLocator("iframe#chatframe");
        Locator chatMessagesLocator = chatFrameLocator.locator("div#items yt-live-chat-text-message-renderer");

        String messageText = extractMessageText(chatMessagesLocator.first());
        assertNotNull(messageText);
        System.out.println("Message: " + messageText);
    }

    @Test
    void testExtractAllMessages() throws InterruptedException {
        Thread.sleep(5000);
        long start = System.nanoTime();
        // Perform a single evaluate call to extract all chat messages.
        List<Map<String, String>> allMessages = (List<Map<String, String>>) page.evaluate("() => {\n" +
                "  return Array.from(document.querySelectorAll('yt-live-chat-text-message-renderer')).map(el => {\n" +
                "    const root = el.shadowRoot || el;\n" +
                "    const username = root.querySelector('#author-name')?.innerText || '';\n" +
                "    const container = root.querySelector('#message');\n" +
                "    let message = '';\n" +
                "    if (container) {\n" +
                "      container.childNodes.forEach(node => {\n" +
                "        if (node.nodeType === Node.TEXT_NODE) {\n" +
                "          message += node.textContent;\n" +
                "        } else if (node.nodeType === Node.ELEMENT_NODE) {\n" +
                "          if (node.tagName.toLowerCase() === 'img') {\n" +
                "            const alt = node.getAttribute('alt');\n" +
                "            message += alt || node.getAttribute('shared-tooltip-text') || '';\n" +
                "          } else {\n" +
                "            message += node.textContent;\n" +
                "          }\n" +
                "        }\n" +
                "      });\n" +
                "    }\n" +
                "    return { username, message };\n" +
                "  });\n" +
                "}");
        long end = System.nanoTime();
        System.out.println("Extracted " + allMessages.size() + " messages in " + (end - start) / 1_000_000.0 + " ms");
        if (!allMessages.isEmpty()) {
            System.out.println("Sample message: " + allMessages.get(0));
        }
    }

    /** Extracts a single username */
    String extractUsername(Locator messageElement) {
        return (String) messageElement.evaluate("""
            (el) => {
                const root = el.shadowRoot || el;
                return root.querySelector('#author-name')?.innerText || '';
            }
        """);
    }

    /** Extracts a single message */
    String extractMessageText(Locator messageElement) {
        return (String) messageElement.evaluate("""
            (el) => {
                const root = el.shadowRoot || el;
                const container = root.querySelector('#message');
                if (!container) return '';

                let result = '';
                container.childNodes.forEach(node => {
                    if (node.nodeType === Node.TEXT_NODE) {
                        result += node.textContent;
                    } else if (node.nodeType === Node.ELEMENT_NODE) {
                        if (node.tagName.toLowerCase() === 'img') {
                            const alt = node.getAttribute('alt');
                            result += alt || node.getAttribute('shared-tooltip-text') || '';
                        } else {
                            result += node.textContent;
                        }
                    }
                });
                return result;
            }
        """);
    }

    /** Batch extraction method */
    Map<String, String> extractBatchMessages(List<Locator> messageElements) {
        return messageElements.stream().collect(Collectors.toMap(
                el -> el.evaluate("el => el.shadowRoot ? el.shadowRoot.querySelector('#author-name')?.innerText : el.querySelector('#author-name')?.innerText").toString(),
                el -> el.evaluate("""
                (el) => {
                    const container = el.shadowRoot
                        ? el.shadowRoot.querySelector('#message')
                        : el.querySelector('#message');
                    if (!container) return '';
                    
                    let result = '';
                    container.childNodes.forEach(node => {
                        if (node.nodeType === Node.TEXT_NODE) {
                            result += node.textContent;
                        } else if (node.nodeType === Node.ELEMENT_NODE) {
                            if (node.tagName.toLowerCase() === 'img') {
                                const alt = node.getAttribute('alt');
                                result += alt || node.getAttribute('shared-tooltip-text') || '';
                            } else {
                                result += node.textContent;
                            }
                        }
                    });
                    return result;
                }
            """).toString()
        ));
    }

    /** Parallel batch extraction method */
    Map<String, String> extractBatchMessagesParallel(List<Locator> messageElements) {
        return messageElements.parallelStream().collect(Collectors.toMap(
                el -> el.evaluate("el => el.shadowRoot ? el.shadowRoot.querySelector('#author-name')?.innerText : el.querySelector('#author-name')?.innerText").toString(),
                el -> el.evaluate("""
                (el) => {
                    const container = el.shadowRoot
                        ? el.shadowRoot.querySelector('#message')
                        : el.querySelector('#message');
                    if (!container) return '';
                    
                    let result = '';
                    container.childNodes.forEach(node => {
                        if (node.nodeType === Node.TEXT_NODE) {
                            result += node.textContent;
                        } else if (node.nodeType === Node.ELEMENT_NODE) {
                            if (node.tagName.toLowerCase() === 'img') {
                                const alt = node.getAttribute('alt');
                                result += alt || node.getAttribute('shared-tooltip-text') || '';
                            } else {
                                result += node.textContent;
                            }
                        }
                    });
                    return result;
                }
            """).toString()
        ));
    }
}