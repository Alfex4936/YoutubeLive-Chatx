// src/main.rs
use anyhow::{Context, Result};
use chromiumoxide::{
    Browser, BrowserConfig, Page,
    detection::{DetectionOptions, default_executable},
    handler::viewport::Viewport,
};
use chrono::Utc;
use clap::Parser;
use futures::StreamExt;
use reqwest::Client;
use rustc_hash::FxHashMap as HashMap;
use serde_json::json;
use std::borrow::Cow;
use std::cmp::Reverse;
use std::collections::BinaryHeap;
// use std::collections::HashMap;
use lasso::ThreadedRodeo;
use std::sync::Mutex as StdMutex;
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
use sysinfo::{ProcessesToUpdate, System, get_current_pid};
use tempfile::tempdir;
use tokio::{
    sync::Mutex,
    time::{Duration, Instant, sleep},
};

mod js_scripts;

#[derive(Clone, Debug, serde::Serialize)]
struct ChatMessage {
    username: Cow<'static, str>,
    message: Cow<'static, str>,
}

#[derive(serde::Serialize)]
struct TopChatter {
    username: String,
    #[serde(rename = "messageCount")]
    message_count: usize,
}

#[derive(Clone, serde::Serialize)]
struct RecentDonator {
    username: String,
    amount: String, // like THB 100.00, $100.00 ...
    message: String,
}

#[derive(Debug, PartialEq)]
enum ScraperStatus {
    Idle,
    Running,
    Failed,
    Completed,
}

impl ScraperStatus {
    fn as_str(&self) -> &'static str {
        match self {
            Self::Idle => "IDLE",
            Self::Running => "RUNNING",
            Self::Failed => "FAILED",
            Self::Completed => "COMPLETED",
        }
    }
}

/// Simple circuit breaker implementation
struct CircuitBreaker {
    last_failure: StdMutex<Instant>, // Largest (typically)
    reset_timeout: Duration,
    threshold: usize,
    failure_count: AtomicUsize,
    state: AtomicBool, // Smallest
}

impl CircuitBreaker {
    fn new(threshold: usize, reset_timeout: Duration) -> Self {
        Self {
            state: AtomicBool::new(true),
            failure_count: AtomicUsize::new(0),
            last_failure: StdMutex::new(Instant::now()),
            threshold,
            reset_timeout,
        }
    }

    fn is_closed(&self) -> bool {
        self.state.load(Ordering::SeqCst)
    }

    fn record_success(&self) {
        if !self.is_closed() {
            // If we've waited long enough since the last failure, try to close the circuit
            let last_failure = self.last_failure.lock().unwrap();
            if last_failure.elapsed() >= self.reset_timeout {
                self.state.store(true, Ordering::SeqCst);
                self.failure_count.store(0, Ordering::SeqCst);
            }
        }
    }

    fn record_failure(&self) -> bool {
        let current_count = self.failure_count.fetch_add(1, Ordering::SeqCst);

        if current_count + 1 >= self.threshold {
            self.state.store(false, Ordering::SeqCst);
            *self.last_failure.lock().unwrap() = Instant::now();
            false // Circuit is now open
        } else {
            true // Circuit is still closed
        }
    }

    async fn execute<F, T>(&self, f: F) -> Result<T>
    where
        F: FnOnce() -> futures::future::BoxFuture<'static, Result<T, anyhow::Error>>,
    {
        if !self.is_closed() {
            let last_failure = *self.last_failure.lock().unwrap();
            if last_failure.elapsed() < self.reset_timeout {
                // Circuit is open and timeout hasn't elapsed
                return Err(anyhow::anyhow!("Circuit breaker is open"));
            }
            // Try to close the circuit for this request
            self.state.store(true, Ordering::SeqCst);
        }

        match f().await {
            Ok(result) => {
                self.record_success();
                Ok(result)
            }
            Err(e) => {
                self.record_failure();
                Err(e)
            }
        }
    }
}

#[derive(Parser)]
struct Args {
    #[arg(long)]
    video_id: String,

    #[arg(long, default_value = "")]
    skip_langs: String,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let args = Args::parse();
    let client = Client::new();
    let created_at = Utc::now().to_rfc3339();
    let total_messages = Arc::new(AtomicUsize::new(0)); // assert_eq!(usize::MAX, 18446744073709551615);
    let is_running = Arc::new(AtomicBool::new(true));

    // Create circuit breakers for backend communication
    // let metrics_breaker = Arc::new(CircuitBreaker::new(5, Duration::from_secs(30)));
    // let messages_breaker = Arc::new(CircuitBreaker::new(3, Duration::from_secs(15)));

    tokio::spawn(handle_signals(Arc::clone(&is_running)));

    eprintln!("💻 Starting scraper for video: {}", args.video_id);

    let skip_langs: Vec<&str> = args
        .skip_langs
        .split(',')
        .filter(|s| !s.is_empty())
        .collect();

    // First set to IDLE
    send_metrics(
        &client,
        &args.video_id,
        &skip_langs,
        ScraperStatus::Idle,
        &created_at,
        None,
        None,
        0,
        0,
        None,
        None,
    )
    .await?;

    // Run main logic
    let scrape_result = scraper_main_logic(
        &args,
        &client,
        &created_at,
        Arc::clone(&total_messages),
        Arc::clone(&is_running),
    )
    .await;

    if let Err(ref e) = scrape_result {
        eprintln!("❌ Scraper encountered an error");

        // Notify Java backend about scraper failure.
        let _ = send_metrics(
            &client,
            &args.video_id,
            &[],
            ScraperStatus::Failed,
            &created_at,
            None,
            None,
            0,
            total_messages.load(Ordering::SeqCst),
            None,
            None,
        )
        .await;
    }

    Ok(())
}

async fn scraper_main_logic(
    args: &Args,
    client: &Client,
    created_at: &str,
    total_messages: Arc<AtomicUsize>,
    is_running: Arc<AtomicBool>,
) -> anyhow::Result<()> {
    let (mut browser, mut handler) = Browser::launch(config_browser()).await?;
    tokio::spawn(async move { while handler.next().await.is_some() {} });

    // Add a HashMap to track message counts by username
    let chatter_counts = Arc::new(Mutex::new(HashMap::<String, usize>::default()));
    // Add a collection to track recent donations (limited to 10)
    let recent_donations = Arc::new(Mutex::new(Vec::<RecentDonator>::with_capacity(10)));

    let page = browser
        .new_page(format!("https://www.youtube.com/watch?v={}", args.video_id))
        .await?;
    page.wait_for_navigation_response().await?;

    // Try to find the chat iframe with proper error handling
    let iframe_url = match wait_for_selector(&page, "iframe#chatframe").await {
        Ok(_) => match get_iframe_url(&page).await {
            Ok(url) => {
                #[cfg(debug_assertions)]
                println!("🔗 Found chat iframe URL: {}", url);
                url
            }
            Err(e) => {
                eprintln!("❌ Failed to get iframe-error URL: {}", e);
                return Err(anyhow::anyhow!("No live chat available for this video"));
            }
        },
        Err(e) => {
            eprintln!("❌ Chat iframe-error not found: {}", e);
            return Err(anyhow::anyhow!("No live chat available for this video"));
        }
    };

    // let iframe_url = get_iframe_url(&page).await?;

    #[cfg(debug_assertions)]
    println!("🔗 Found chat iframe URL: {}", iframe_url);

    let video_title: String = page
        .evaluate(js_scripts::VIDEO_TITLE)
        .await?
        .into_value()
        .unwrap_or_default();
    let channel_name: String = page
        .evaluate(js_scripts::CHANNEL_NAME)
        .await?
        .into_value()
        .unwrap_or_default();

    page.close().await?;
    let iframe_page = browser.new_page(&iframe_url).await?;

    setup_js_bindings(&iframe_page).await?;

    let message_batch = Arc::new(Mutex::new(Vec::new()));
    tokio::spawn(handle_chat_events(
        iframe_page.clone(),
        Arc::clone(&message_batch),
        Arc::clone(&chatter_counts),
        Arc::clone(&recent_donations),
    ));

    iframe_page.evaluate(js_scripts::CHAT_OBSERVER).await?; // also checking donation

    send_metrics(
        &client,
        &args.video_id,
        &[],
        ScraperStatus::Running,
        &created_at,
        Some(&video_title),
        Some(&channel_name),
        0,
        0,
        None,
        None,
    )
    .await?;

    eprintln!("initiated"); // for java

    // If there's no activity for 30 mins, it's probably over
    let inactivity_limit = Duration::from_secs(30 * 60); // 30 minutes
    let check_chat_deadline = Duration::from_secs(15 * 60); // 15 minutes
    let mut last_message_time = Instant::now();
    let mut last_chat_check = Instant::now();
    let mut consecutive_empty_intervals = 0 as u64; // Track consecutive intervals with no messages

    let max_retries = 3;

    // TODO: Update video title every n minutes

    #[cfg(debug_assertions)]
    let mut sys = System::new_all();

    // Pre-allocate batch vector to avoid repeated allocations
    // which fits nicely in most L1 caches (typically 32-64KB)
    let mut batch = Vec::with_capacity(64); // Reasonable initial capacity

    loop {
        if !is_running.load(Ordering::Relaxed) {
            // Use Relaxed ordering for better performance
            break;
        }

        let mut wait_duration = Duration::from_secs(1);
        let mut retries = 0;

        #[cfg(debug_assertions)]
        {
            // Prepare the list of PIDs to update: always include the current process.
            let current_pid = get_current_pid().expect("Failed to get current PID");
            let pids = vec![current_pid];
            sys.refresh_processes(ProcessesToUpdate::Some(&pids), true);
            log_rust_usage(&sys);
        }

        // Clear batch instead of creating a new Vec
        batch.clear();

        // Minimize lock duration by using a scope
        {
            let mut locked_batch = message_batch.lock().await;
            batch.append(&mut *locked_batch); // Append is more efficient than swap for growing collections
        }

        let interval_messages = batch.len();

        if !batch.is_empty() {
            last_message_time = Instant::now();
            consecutive_empty_intervals = 0;

            // Fire and forget - don't wait for result if not needed
            tokio::spawn({
                let client = client.clone();
                let video_id = args.video_id.clone();
                let batch_clone = batch.clone(); // Only clone when needed
                async move {
                    let _ = send_messages_to_backend(&client, &video_id, &batch_clone).await;
                }
            });

            // Update total_messages atomically
            total_messages.fetch_add(interval_messages, Ordering::Relaxed);

            #[cfg(debug_assertions)]
            println!(
                "Added {} messages, total now: {}",
                interval_messages,
                total_messages.load(Ordering::Relaxed)
            );
        } else {
            consecutive_empty_intervals += 1;
        }

        if !is_running.load(Ordering::Relaxed) {
            break;
        }

        // Only get top chatters when needed (not every iteration)
        let top_chatters = if consecutive_empty_intervals % 3 == 0 {
            get_top_chatters(&chatter_counts, 5).await
        } else {
            Vec::new() // Empty vec when not needed
        };

        let recent_donators = get_recent_donators(&recent_donations).await;

        let result = send_metrics(
            &client,
            &args.video_id,
            &[],
            ScraperStatus::Running,
            &created_at,
            Some(&video_title),
            Some(&channel_name),
            interval_messages,
            total_messages.load(Ordering::Relaxed),
            if top_chatters.is_empty() {
                None
            } else {
                Some(&top_chatters)
            },
            if recent_donators.is_empty() {
                None
            } else {
                Some(&recent_donators)
            },
        )
        .await;

        // Rest of the loop logic for error handling and checking
        if let Err(_) = result {
            retries += 1;
            // eprintln!("⚠️ send_metrics failed (attempt {}): {}", retries, e);

            if retries >= max_retries {
                eprintln!("❌ Maximum retries reached, stopping scraper...");
                is_running.store(false, Ordering::SeqCst);
                break;
            }

            sleep(wait_duration).await;
            wait_duration *= 2; // Exponential backoff
        }

        let time_since_last_message = Instant::now().duration_since(last_message_time);
        let time_since_last_check = Instant::now().duration_since(last_chat_check);

        // Combine conditions for better readability and performance
        let should_check_chat = (consecutive_empty_intervals >= 3
            && time_since_last_check >= Duration::from_secs(60))
            || (time_since_last_message >= Duration::from_secs(5 * 60)
                && time_since_last_check >= Duration::from_secs(30))
            || (time_since_last_message >= check_chat_deadline
                && time_since_last_check >= Duration::from_secs(60));

        if should_check_chat {
            #[cfg(debug_assertions)]
            println!(
                "Checking if chat has ended after {} consecutive empty intervals",
                consecutive_empty_intervals
            );

            // Use a timeout to prevent hanging on network requests
            match tokio::time::timeout(
                Duration::from_secs(5),
                has_live_chat_ended(&client, &iframe_url),
            )
            .await
            {
                Ok(Ok(true)) => {
                    #[cfg(debug_assertions)]
                    println!("🔴 Live chat is unavailable (detected via HTTP request). Stopping.");
                    break;
                }
                Ok(Ok(false)) => {}
                _ => {
                    #[cfg(debug_assertions)]
                    println!("⚠️ Chat check timed out or failed, continuing...");
                }
            }
            last_chat_check = Instant::now();
        }

        // If no activity for 30 minutes, exit without checking further
        if time_since_last_message >= inactivity_limit {
            #[cfg(debug_assertions)]
            println!("⏳ No messages for 30 minutes. Exiting.");
            break;
        }

        // Adaptive sleep duration based on activity
        let sleep_duration = if consecutive_empty_intervals > 5 {
            // Shorter sleep when we suspect the stream might be ending
            Duration::from_secs(5)
        } else {
            Duration::from_secs(10)
        };

        sleep(sleep_duration).await;
    }

    // Include top chatters in final metrics
    let top_chatters = get_top_chatters(&chatter_counts, 5).await;
    let recent_donations = get_recent_donators(&recent_donations).await;
    send_metrics(
        &client,
        &args.video_id,
        &[],
        ScraperStatus::Completed,
        &created_at,
        Some(&video_title),
        Some(&channel_name),
        0,
        total_messages.load(Ordering::SeqCst),
        Some(&top_chatters),
        Some(&recent_donations),
    )
    .await?;

    iframe_page.close().await.ok();
    browser.close().await.ok();
    browser.kill().await.unwrap().ok();

    Ok(())
}

async fn handle_signals(is_running: Arc<AtomicBool>) {
    wait_for_signal().await;
    is_running.store(false, Ordering::SeqCst);
    #[cfg(debug_assertions)]
    println!("Graceful shutdown triggered");
}

#[cfg(unix)]
async fn wait_for_signal() {
    use tokio::io::{self, AsyncBufReadExt, BufReader};
    use tokio::signal::unix::{SignalKind, signal};

    let mut signal_terminate = signal(SignalKind::terminate()).unwrap();
    let mut signal_interrupt = signal(SignalKind::interrupt()).unwrap();

    let mut stdin = BufReader::new(io::stdin()).lines();

    tokio::select! {
        _ = stdin.next_line() => {
            #[cfg(debug_assertions)]
            println!("Detected parent process exit. Shutting down...");
        }
        _ = signal_terminate.recv() => println!("Received SIGTERM."),
        _ = signal_interrupt.recv() => println!("Received SIGINT."),
    };
}

#[cfg(windows)]
async fn wait_for_signal() {
    use tokio::{
        io::{self, AsyncBufReadExt, BufReader},
        signal::windows,
    };

    let mut signal_c = windows::ctrl_c().unwrap();
    let mut signal_break = windows::ctrl_break().unwrap();
    let mut signal_close = windows::ctrl_close().unwrap();
    let mut signal_shutdown = windows::ctrl_shutdown().unwrap();

    let mut stdin = BufReader::new(io::stdin()).lines();

    tokio::select! {
        _ = stdin.next_line() => { // Detect Java killing Rust by closing `stdin`
            #[cfg(debug_assertions)]
            println!("Detected parent process exit. Shutting down...");
        }
        _ = signal_c.recv() => println!("Received CTRL_C."),
        _ = signal_break.recv() => println!("Received CTRL_BREAK."),
        _ = signal_close.recv() => println!("Received CTRL_CLOSE."),
        _ = signal_shutdown.recv() => println!("Received CTRL_SHUTDOWN."),
    };
}

async fn send_metrics(
    client: &Client,
    video_id: &str,
    skip_langs: &[&str],
    status: ScraperStatus,
    created_at: &str,
    video_title: Option<&str>,
    channel_name: Option<&str>,
    messages_in_last_interval: usize,
    total_messages: usize,
    top_chatters: Option<&[TopChatter]>,
    recent_donators: Option<&[RecentDonator]>,
) -> Result<()> {
    let mut body = json!({
        "videoId": video_id,
        "status": status.as_str(),
        "createdAt": created_at,
        "messagesInLastInterval": messages_in_last_interval,
        "totalMessages": total_messages,
    });

    if status == ScraperStatus::Idle {
        body["skipLangs"] = json!(skip_langs);
    }

    if status == ScraperStatus::Idle || status == ScraperStatus::Running {
        if let Some(title) = video_title {
            body["videoTitle"] = json!(title);
        }
        if let Some(channel) = channel_name {
            body["channelName"] = json!(channel);
        }
    }

    // Add top chatters to metrics if available
    if let Some(chatters) = top_chatters {
        body["topChatters"] = json!(chatters);
    }

    // Add recent donators to metrics if available
    if let Some(donators) = recent_donators {
        body["recentDonations"] = json!(donators);
    }

    client
        .patch("http://localhost:8080/scrapers/updateMetrics")
        .json(&body)
        .send()
        .await?;

    Ok(())
}

async fn send_messages_to_backend(
    client: &Client,
    video_id: &str,
    messages: &[ChatMessage],
) -> Result<()> {
    client
        .post("http://localhost:8080/scrapers/messages")
        .json(&json!({
            "videoId": video_id,
            "messages": messages,
        }))
        .send()
        .await?;
    Ok(())
}

fn config_browser() -> BrowserConfig {
    let user_data_dir = tempdir().expect("Failed to create temp dir");
    // let chrome_path = "D:\\SDK\\ungoogled-chromium_134.0.6998.35-1.1_windows_x64\\chrome.exe";
    BrowserConfig::builder()
        .no_sandbox()
        .chrome_executable(
            // chrome_path,
            default_executable(DetectionOptions {
                msedge: false,
                unstable: true,
            })
            .unwrap(),
        )
        // .disable_cache()
        .headless_mode(chromiumoxide::browser::HeadlessMode::True)
        .args([
            "--single-process",
            "--no-zygote",
            "--no-startup-window",
            "--enable-low-end-device-mode", // Optimize for lower memory usage
            "--memory-pressure-off",        // Prevent aggressive memory reclaiming
            "--remote-debugging-port=0",
            "--disable-popup-blocking",
            "--disable-crash-reporter",
            "--disable-sync-preferences",
            "--disable-background-timer-throttling",
            "--disable-renderer-backgrounding",
            "--no-sandbox",
            "--disable-extensions",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--disable-setuid-sandbox",
            "--disable-accelerated-2d-canvas",
            "--disable-web-security",
            "--disable-default-apps",
            "--disable-sync",
            "--disable-translate",
            "--metrics-recording-only",
            "--mute-audio",
            "--no-first-run",
            "--disable-backgrounding-occluded-windows",
            "--disable-blink-features=AutomationControlled", // Hides automation
            &format!("--user-data-dir={}", user_data_dir.path().display()),
            "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) \
              AppleWebKit/537.36 (KHTML, like Gecko) \
              Chrome/133.0.0.0 Safari/537.36",
        ])
        .viewport(Some(Viewport {
            width: 1280,
            height: 720,
            device_scale_factor: Some(1.0),
            emulating_mobile: false,
            is_landscape: true,
            has_touch: false,
        }))
        .build()
        .unwrap()
}

async fn wait_for_selector(page: &Page, selector: &str) -> Result<()> {
    for _ in 0..5 {
        if page.find_element(selector).await.is_ok() {
            return Ok(());
        }
        sleep(Duration::from_secs(2)).await;
    }
    anyhow::bail!("selector `{}` not found", selector)
}

async fn get_iframe_url(page: &Page) -> Result<String> {
    page.find_element("iframe#chatframe")
        .await?
        .description()
        .await?
        .content_document
        .and_then(|doc| doc.document_url)
        .context("iframe URL not found")
}

async fn setup_js_bindings(page: &Page) -> Result<()> {
    page.expose_function("rustChatHandler", js_scripts::RUST_HANDLER)
        .await?;
    page.expose_function("rustDonationChatHandler", js_scripts::RUST_DONOR_HANDLER)
        .await?;
    Ok(())
}

async fn get_top_chatters(
    chatter_counts: &Arc<Mutex<HashMap<String, usize>>>,
    count: usize,
) -> Vec<TopChatter> {
    let counts = chatter_counts.lock().await;

    // Skip the work if there are no chatters or count is zero
    if counts.is_empty() || count == 0 {
        return Vec::new();
    }

    // If we have fewer entries than requested count, no need for heap
    if counts.len() <= count {
        return counts
            .iter()
            .map(|(username, &message_count)| TopChatter {
                username: username.clone(),
                message_count,
            })
            .collect();
    }

    // Use a min-heap for efficient top-N extraction
    let mut heap = BinaryHeap::with_capacity(count);

    // Pre-fill with first 'count' elements to avoid unnecessary pushes/pops
    let mut iter = counts.iter();
    for _ in 0..count {
        if let Some((username, &message_count)) = iter.next() {
            heap.push(Reverse((message_count, username)));
        } else {
            break;
        }
    }

    // Process remaining elements
    for (username, &message_count) in iter {
        let min = heap.peek().unwrap().0;
        if message_count > min.0 {
            heap.pop();
            heap.push(Reverse((message_count, username)));
        }
    }

    // Convert to Vec in descending order (most messages first)
    let mut result = Vec::with_capacity(heap.len());
    for Reverse((count, name)) in heap.into_sorted_vec().into_iter().rev() {
        result.push(TopChatter {
            username: name.clone(),
            message_count: count,
        });
    }

    result
}

async fn get_recent_donators(
    recent_donations: &Arc<Mutex<Vec<RecentDonator>>>,
) -> Vec<RecentDonator> {
    let donations = recent_donations.lock().await;
    donations.clone()
}

async fn handle_chat_events(
    page: Page,
    message_batch: Arc<Mutex<Vec<ChatMessage>>>,
    chatter_counts: Arc<Mutex<HashMap<String, usize>>>,
    recent_donations: Arc<Mutex<Vec<RecentDonator>>>,
) -> Result<()> {
    let mut events = page
        .event_listener::<chromiumoxide::cdp::js_protocol::runtime::EventBindingCalled>()
        .await?;

    // Create a thread-safe string interner
    let interner = ThreadedRodeo::default();

    while let Some(event) = events.next().await {
        if event.name == "rustChatHandler" {
            let parts: Vec<&str> = event.payload.split('\t').collect();
            if parts.len() == 2 {
                // Intern the username - this is very cache-friendly
                let username_key = interner.get_or_intern(parts[0]);
                let username = interner.resolve(&username_key).to_string();
                let message = Cow::Owned(parts[1].to_string());

                // Update chatter counts
                {
                    let mut counts = chatter_counts.lock().await;
                    *counts.entry(username.clone()).or_insert(0) += 1;
                }

                let chat_msg = ChatMessage {
                    username: Cow::Owned(username),
                    message,
                };

                message_batch.lock().await.push(chat_msg);
            }
        } else if event.name == "rustDonationChatHandler" {
            // Handle donation messages
            let parts: Vec<&str> = event.payload.split('\t').collect();
            if parts.len() == 3 {
                let username = parts[0].to_string();
                let amount = parts[1].to_string();
                let message = parts[2].to_string();

                // Add to recent donations, keeping only the most recent ones
                let mut donations = recent_donations.lock().await;

                // More efficient: if at capacity, remove last item before adding new one
                if donations.len() >= 10 {
                    // Use swap_remove for O(1) removal (order not preserved)
                    // or rotate_left + truncate to maintain chronological order
                    donations.rotate_left(1);
                    donations.truncate(9);
                }

                // Add new donation at the end
                donations.push(RecentDonator {
                    username,
                    amount,
                    message,
                });
            }
        }
    }
    Ok(())
}

/// Logs resource usage for the current Rust process using sysinfo.
fn log_rust_usage(sys: &System) {
    let current_pid = get_current_pid().expect("Failed to get current PID");
    if let Some(process) = sys.process(current_pid) {
        #[cfg(debug_assertions)]
        println!(
            "💻 Rust Process - CPU: {:.2}%, Memory: {:.2} MB",
            process.cpu_usage(),
            process.memory() as f64 / (1024.0 * 1024.0) // Convert bytes to MB
        );
    } else {
        #[cfg(debug_assertions)]
        println!("Could not retrieve Rust process info.");
    }
}

async fn has_live_chat_ended(client: &Client, iframe_url: &str) -> anyhow::Result<bool> {
    let res = client
        .get(iframe_url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
        .send()
        .await?
        .text()
        .await?;

    // Check if the response contains "채팅을 사용할 수 없는 실시간 스트림입니다."
    Ok(res.contains("채팅을 사용할 수 없는 실시간 스트림입니다."))
}

/*
<yt-formatted-string id="text" class="style-scope yt-live-chat-message-renderer">
    채팅을 사용할 수 없는 실시간 스트림입니다.
</yt-formatted-string>

https://www.youtube.com/live?app=desktop&persist_gl=1&gl=US

https://www.youtube.com/live_chat?is_popout=1&v=kPbEAbLCX7g

TODO: we dont have to find iframe as above link works
*/
