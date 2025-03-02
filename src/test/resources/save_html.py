from playwright.sync_api import sync_playwright
import time

url = "https://www.youtube.com/watch?v=LNtw5Ham47k"

with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.goto(url, wait_until="load")

    time.sleep(10)  # Wait for dynamic content to load

    # Get the main page HTML
    main_html = page.content()

    # Extract iframes and inline their content
    iframes = page.frames
    iframe_replacements = {}

    for index, iframe in enumerate(iframes):
        if iframe.url:
            try:
                iframe_html = iframe.content()
                iframe_replacements[f'src="{iframe.url}"'] = f"<iframe>{iframe_html}</iframe>"
            except Exception as e:
                print(f"Failed to get content for iframe {index}: {e}")

    # Replace external iframe URLs with inline content
    for iframe_src, iframe_content in iframe_replacements.items():
        main_html = main_html.replace(iframe_src, iframe_content)

    # Save the final HTML with embedded iframe content
    with open("saved_full_page.html", "w", encoding="utf-8") as f:
        f.write(main_html)

    browser.close()
    print("Full page (including iframes) saved successfully!")
