// src/js_scripts.rs

pub const VIDEO_TITLE: &str = r##"
() => {
    const watchMeta = document.querySelector("ytd-watch-metadata");
    if (!watchMeta) return "";
    const titleEl = watchMeta.querySelector("#title h1 yt-formatted-string");
    return titleEl ? titleEl.textContent.trim() : "";
}
"##;

pub const CHANNEL_NAME: &str = r##"
() => {
    const watchMeta = document.querySelector("ytd-watch-metadata");
    if (!watchMeta) return "";
    const ownerEl = watchMeta.querySelector("#owner ytd-video-owner-renderer");
    if (!ownerEl) return "";
    const channelLink = ownerEl.querySelector("#channel-name #container #text-container yt-formatted-string a");
    return channelLink ? channelLink.textContent.trim() : "";
}
"##;

pub const RUST_HANDLER: &str = r#"
(data) => {
    let parts = data.split('\t');
    if (parts.length === 2) {
        let username = parts[0];
        let message = parts[1];
        window._sendToRust(username, message);
    }
}
"#;

pub const CHAT_OBSERVER: &str = r##"
(function() {
    try {
        function extractMessageData(messageElement) {
            const username = messageElement.querySelector("#author-name")?.innerText.trim() || "Unknown User";
            const container = messageElement.shadowRoot 
                ? messageElement.shadowRoot.querySelector("#message") 
                : messageElement.querySelector("#message");
            
            if (!container) return;

            let messageText = "";
            container.childNodes.forEach(node => {
                if (node.nodeType === Node.TEXT_NODE) {
                    messageText += node.textContent;
                } else if (node.nodeType === Node.ELEMENT_NODE && node.tagName.toLowerCase() === "img") {
                    const emoji = node.getAttribute("shared-tooltip-text") || node.getAttribute("alt") || "";
                    messageText += emoji;
                }
            });

            if (username && messageText) {
                window.rustChatHandler(username + "\t" + messageText);
            }
        }

        const chatContainer = document.querySelector("div#items");
        if (!chatContainer) {
            console.error("Chat container not found");
            return;
        }
        console.log("✅ MutationObserver started");

        if (window._chatObserver) {
            window._chatObserver.disconnect();
        }

        const observer = new MutationObserver((mutations) => {
            mutations.forEach(mutation => {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType === Node.ELEMENT_NODE && node.matches("yt-live-chat-text-message-renderer")) {
                        extractMessageData(node);
                    }
                });
            });
        });

        observer.observe(chatContainer, { childList: true });
        window._chatObserver = observer;

        console.log("✅ MutationObserver setup complete");
    } catch (error) {
        console.error("❌ Observer setup failed", error);
    }
})();
"##;

pub const DONATION_OBSERVER: &str = r#"
(function() {
    const observer = new MutationObserver(mutations => {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                if (node.tagName === 'YT-LIVE-CHAT-PAID-MESSAGE-RENDERER') {
                    try {
                        const username = node.querySelector('#author-name').textContent.trim();
                        const amountEl = node.querySelector('#purchase-amount');
                        const amount = amountEl ? amountEl.textContent.trim() : 'Unknown';
                        const messageEl = node.querySelector('#message');
                        const message = messageEl ? messageEl.textContent.trim() : '';
                        const timestampEl = node.querySelector('#timestamp');
                        const timestamp = timestampEl ? timestampEl.textContent.trim() : '';
                        
                        window.rustDonationHandler(username, amount, message, timestamp);
                    } catch (e) {
                        console.error('Error processing donation:', e);
                    }
                }
            }
        }
    });
    
    observer.observe(document.querySelector('yt-live-chat-item-list-renderer #items'), {
        childList: true,
        subtree: true
    });
    
    console.log('Donation observer started');
})();
"#;

pub const CHAT_ENDED_CHECK: &str = r#"
    (() => {
        return !!document.querySelector('yt-formatted-string#text.style-scope.yt-live-chat-message-renderer');
    })()
"#;
