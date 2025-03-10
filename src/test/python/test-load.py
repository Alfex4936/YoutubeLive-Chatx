from locust import HttpUser, task, events
import psutil
import re
import threading
from playwright.sync_api import sync_playwright

def parse_viewer_count(text):
    """Convert YouTube viewer count text to an integer."""
    text = text.replace("명 시청 중", "").strip()

    if "만" in text:  # Example: "1.2만" -> 12000
        num = float(text.replace("만", "")) * 10000
    elif "천" in text:  # Example: "8.5천" -> 8500
        num = float(text.replace("천", "")) * 1000
    else:
        num = int(re.sub(r"[^\d]", "", text))  # Remove non-numeric characters

    return int(num)


def get_live_video_ids():
    """Scrape YouTube Live for active video IDs and sort them by viewer count."""
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto("https://www.youtube.com/live?app=desktop", timeout=60000)

        video_data = []  # Store tuples of (video_id, viewer_count)
        total_viewers = 0
        elements = page.locator('ytd-rich-grid-media').all()

        for element in elements:
            if element.locator("text=시청 중").count() > 0:
                link = element.locator("#video-title-link").get_attribute("href")
                if link and "/live/" in link:
                    video_id = link.split("/live/")[-1]
                    video_id = re.sub(r"\?.*$", "", video_id)  # Remove query parameters

                    # Extract viewer count
                    viewer_text = element.locator("span.inline-metadata-item").inner_text()
                    viewer_count = parse_viewer_count(viewer_text) if viewer_text else 0
                    total_viewers += viewer_count

                    video_data.append((video_id, viewer_count))

        browser.close()

        # Sort videos by viewer count in descending order
        sorted_video_data = sorted(video_data, key=lambda x: x[1], reverse=True)
        sorted_video_ids = [video_id for video_id, _ in sorted_video_data]

        print(f"Total Viewers Watching Live: {total_viewers:,}")
        return sorted_video_ids[:10]  # Return top 10 videos


class ScraperUser(HttpUser):
    """Locust User that calls /start-scraper for all videos at once and monitors CPU/memory."""
    video_ids = []

    @events.test_start.add_listener
    def setup_scraper_users(environment, **kwargs):
        """Fetch live video IDs at the start of the test."""
        ScraperUser.video_ids = get_live_video_ids()
        if not ScraperUser.video_ids:
            print("No live video IDs found! Exiting...")
            environment.runner.quit()  # Stop Locust if no videos are found

    @task
    def start_scrapers(self):
        """Run all scrapers at once by calling /start-scraper for each video."""
        threads = []
        for video_id in ScraperUser.video_ids:
            thread = threading.Thread(target=self.call_scraper_api, args=(video_id,))
            thread.start()
            threads.append(thread)

        for thread in threads:
            thread.join()  # Wait for all scrapers to finish

        self.monitor_system_usage()

        # Stop Locust after all scrapers have finished
        self.environment.runner.quit()

    def call_scraper_api(self, video_id):
        """Call the /start-scraper API to trigger scrapers for each video ID."""
        url = f"/scrapers/start-scraper?videoId={video_id}"
        with self.client.get(url, name="Start Scraper", catch_response=True) as response:
            if response.status_code == 302:
                response.success()
                print(f"✅ Started scraper for {video_id}")
            else:
                response.failure(f"❌ Failed to start scraper for {video_id} (Status: {response.status_code})")

    def monitor_system_usage(self):
        """Print CPU and RAM usage after all scrapers have run."""
        mem = psutil.virtual_memory()
        cpu = psutil.cpu_percent(interval=1)
        print(f"💻 CPU Usage: {cpu}% | RAM Usage: {mem.percent}%")

