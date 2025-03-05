from playwright.sync_api import sync_playwright
import re

def parse_viewer_count(text):
    """
    Convert YouTube viewer count text to an integer.
    Handles:
    - "1만명 시청 중" -> 10000
    - "8.5천명 시청 중" -> 8500
    - "891명 시청 중" -> 891
    """
    text = text.replace("명 시청 중", "").strip()  # Remove extra words

    if "만" in text:  # Example: "1.2만" -> 12000
        num = float(text.replace("만", "")) * 10000
    elif "천" in text:  # Example: "8.5천" -> 8500
        num = float(text.replace("천", "")) * 1000
    else:
        num = int(re.sub(r"[^\d]", "", text))  # Remove non-numeric characters

    return int(num)

def get_live_video_ids():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto("https://www.youtube.com/live?app=desktop", timeout=60000)

        video_ids = set()
        total_viewers = 0
        elements = page.locator('ytd-rich-grid-media').all()

        for element in elements:
            if element.locator("text=시청 중").count() > 0:
                link = element.locator("#video-title-link").get_attribute("href")
                if link and "/live/" in link:
                    video_id = link.split("/live/")[-1]
                    video_id = re.sub(r"\?.*$", "", video_id)  # Remove query parameters
                    video_ids.add(video_id)

                # Extract viewer count
                viewer_text = element.locator("span.inline-metadata-item").inner_text()
                viewer_count = parse_viewer_count(viewer_text) if viewer_text else 0
                total_viewers += viewer_count

        browser.close()

        print(f"Total Viewers Watching Live: {total_viewers:,}")
        return list(video_ids)

# Test it:
if __name__ == "__main__":
    live_videos = get_live_video_ids()
    print(f"Live Video IDs: {live_videos}")
