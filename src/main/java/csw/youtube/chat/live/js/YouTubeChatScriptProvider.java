package csw.youtube.chat.live.js;


public class YouTubeChatScriptProvider {

    public static String getExposeHandlerScript() {
        return "window._ytChatHandler = {};";
    }

    public static String getChatActivationScript() {
        return """
                    () => {
                        try {
                            const iframe = document.querySelector("iframe#chatframe");
                            if (iframe) {
                                const chatDoc = iframe.contentDocument || iframe.contentWindow.document;
                                const chatContainer = chatDoc.querySelector("div#item-scroller");
                                if (!chatContainer) {
                                    console.error("❌ Chat container not found.");
                                    return;
                                }
                                console.log("✅ Keeping chat active...");
                
                                // 🟢 Trick YouTube into thinking the tab is active
                                setInterval(() => {
                                    document.dispatchEvent(new Event("visibilitychange")); // Fake visibility change
                                    window.dispatchEvent(new Event("focus")); // Fake focus event
                                }, 5000);
                
                                // 🟢 Ensure chat is always scrolled to the bottom
                                setInterval(() => {
                                    chatContainer.scrollTop = chatContainer.scrollHeight;
                                }, 3000);
                
                                // 🟢 Fake user activity (mouse movement)
                                setInterval(() => {
                                    document.dispatchEvent(new MouseEvent("mousemove"));
                                }, 10000);
                            }
                        } catch (error) {
                            console.error("❌ Error keeping chat active:", error);
                        }
                    }
                """;
    }

    public static String getMutationObserverScript() {
        return """
                    () => {
                        try {
                            // Define the extractMessageData function
                            function extractMessageData(messageElement) {
                                const username = messageElement.querySelector("#author-name")?.innerText.trim() || "Unknown User";
                                const container = messageElement.shadowRoot
                                    ? messageElement.shadowRoot.querySelector('#message')
                                    : messageElement.querySelector('#message');
                                if (!container) return { username, messageText: '' };
                
                                let messageText = '';
                                Array.from(container.childNodes).forEach(node => {
                                    if (node.nodeType === Node.TEXT_NODE) {
                                        messageText += node.textContent;
                                    } else if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === 'img') {
                                        const emoji = node.getAttribute('shared-tooltip-text') || node.getAttribute('alt') || '';
                                        messageText += emoji;
                                    }
                                });
                                return { username, messageText };
                            }
                
                            // Find the chat container
                            const chatContainer = document.querySelector('div#items');
                            if (!chatContainer) {
                                console.error("Chat container not found");
                                return;
                            }
                            console.log("MutationObserver started");
                
                            // Disconnect any existing observer
                            if (window._chatObserver) {
                                window._chatObserver.disconnect();
                            }
                
                            // Set up the MutationObserver
                            const observer = new MutationObserver((mutations) => {
                                mutations.forEach(mutation => {
                                    mutation.addedNodes.forEach(node => {
                                        if (node.nodeType === Node.ELEMENT_NODE && node.matches("yt-live-chat-text-message-renderer")) {
                                            const { username, messageText } = extractMessageData(node);
                                            window._ytChatHandler_onNewMessages(username, messageText);
                                        }
                                    });
                                });
                            });
                
                            // Start observing the chat container
                            observer.observe(chatContainer, { childList: true });
                            window._chatObserver = observer;
                            console.log("MutationObserver setup complete");
                        } catch (error) {
                            console.error("Observer setup failed", error);
                        }
                    }
                """;
    }

    public static String extractMessageText() {
        return """
                    (el) => {
                        // Get the message container from shadow DOM if available
                        const container = el.shadowRoot 
                            ? el.shadowRoot.querySelector('#message') 
                            : el.querySelector('#message');
                        if (!container) return '';
                
                        // Use XPath to get all descendant nodes in order
                        const xpathResult = document.evaluate(
                            './/node()', container, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null
                        );
                
                        let result = '';
                        for (let i = 0; i < xpathResult.snapshotLength; i++) {
                            const node = xpathResult.snapshotItem(i);
                            if (node.nodeType === Node.TEXT_NODE) {
                                result += node.textContent;
                            } else if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === 'img') {
                                // Prefer shared-tooltip-text (which is often formatted like :emoji:) if available
                                const emoji = node.getAttribute('shared-tooltip-text') || node.getAttribute('alt') || '';
                                result += emoji;
                            }
                        }
                        return result;
                    }
                """;
    }

    public static String extractVideoTitle() {
        return """
                () => {
                  const watchMeta = document.querySelector("ytd-watch-metadata");
                  if (!watchMeta) return "";
                  const titleEl = watchMeta.querySelector("#title h1 yt-formatted-string");
                  return titleEl ? titleEl.textContent.trim() : "";
                }
                """;
    }

    public static String extractChannelName() {
        return """
                () => {
                  const watchMeta = document.querySelector("ytd-watch-metadata");
                  if (!watchMeta) return "";
                  const ownerEl = watchMeta.querySelector("#owner ytd-video-owner-renderer");
                  if (!ownerEl) return "";
                  const channelLink = ownerEl.querySelector("#channel-name #container #text-container yt-formatted-string a");
                  return channelLink ? channelLink.textContent.trim() : "";
                }
                """;
    }

    public static String getDisconnectObserverScript() {
        return """
                    () => {
                        if (window._chatObserver) {
                            window._chatObserver.disconnect();
                            window._chatObserver = null;
                        }
                    }
                """;
    }

}
