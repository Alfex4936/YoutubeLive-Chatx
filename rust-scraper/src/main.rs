// src/main.rs
use anyhow::{Context, Result};

use chromiumoxide::cdp::browser_protocol::system_info::ProcessInfo;
use chromiumoxide::{detection::{default_executable, DetectionOptions}, handler::viewport::Viewport, Browser, BrowserConfig, Element, Page};
use chrono::Utc;
use clap::Parser;
use futures::StreamExt;
use reqwest::Client;
use serde_json::json;
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
use std::mem;
use sysinfo::{MINIMUM_CPU_UPDATE_INTERVAL, Pid, ProcessesToUpdate, System, get_current_pid};
use tokio::{
    sync::Mutex,
    time::{sleep, Duration, Instant},
};
use tempfile::tempdir;

mod js_scripts;

#[derive(Clone, Debug, serde::Serialize)]
struct ChatMessage {
    username: String,
    message: String,
}

#[derive(Debug)]
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
    let total_messages = Arc::new(AtomicUsize::new(0));
    let is_running = Arc::new(AtomicBool::new(true));

    tokio::spawn(handle_signals(Arc::clone(&is_running)));

    eprintln!("💻 Starting scraper for video: {}", args.video_id);

    // First set to IDLE
    send_metrics(&client, &args.video_id, &[], ScraperStatus::Idle, &created_at, None, None, 0, 0).await?;

    // Run main logic
    let scrape_result = scraper_main_logic(&args, &client, &created_at, Arc::clone(&total_messages), Arc::clone(&is_running)).await;

    if let Err(ref e) = scrape_result {
        eprintln!("❌ Scraper encountered an error");

        // Notify Java backend about scraper failure.
        let _ = send_metrics(
            &client,
            &args.video_id,
            &args.skip_langs.split(',').collect::<Vec<_>>(),
            ScraperStatus::Failed,
            &created_at,
            None,
            None,
            0,
            total_messages.load(Ordering::SeqCst),
        ).await;
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
    let skip_langs: Vec<&str> = args
        .skip_langs
        .split(',')
        .filter(|s| !s.is_empty())
        .collect();

    let (mut browser, mut handler) = Browser::launch(config_browser()).await?;
    tokio::spawn(async move { while handler.next().await.is_some() {} });

    let page = browser
        .new_page(format!("https://www.youtube.com/watch?v={}", args.video_id))
        .await?;
    page.wait_for_navigation_response().await?;

    wait_for_selector(&page, "iframe#chatframe").await?;
    let iframe_url = get_iframe_url(&page).await?;

    #[cfg(debug_assertions)]
    println!("🔗 Found chat iframe URL: {}", iframe_url);

    let video_title: String = page.evaluate(js_scripts::VIDEO_TITLE).await?.into_value().unwrap_or_default();
    let channel_name: String = page.evaluate(js_scripts::CHANNEL_NAME).await?.into_value().unwrap_or_default();

    page.close().await?;
    let iframe_page = browser.new_page(&iframe_url).await?;

    setup_js_bindings(&iframe_page).await?;

    let message_batch = Arc::new(Mutex::new(Vec::new()));
    tokio::spawn(handle_chat_events(iframe_page.clone(), Arc::clone(&message_batch)));

    iframe_page.evaluate(js_scripts::CHAT_OBSERVER).await?;

    send_metrics(
        &client,
        &args.video_id,
        &skip_langs,
        ScraperStatus::Running,
        &created_at,
        Some(&video_title),
        Some(&channel_name),
        0,
        0,
    ).await?;

    // If there's no activity for 30 mins, it's probably over
    let inactivity_limit = Duration::from_secs(30 * 60); // 30 minutes
    let check_chat_deadline = Duration::from_secs(15 * 60); // 15 minutes
    let mut last_message_time = Instant::now();
    let mut last_chat_check = Instant::now();

    let mut sys = System::new_all();
    loop {
        if !is_running.load(Ordering::SeqCst) {
            break;
        }

        // Prepare the list of PIDs to update: always include the current process.
        let current_pid = get_current_pid().expect("Failed to get current PID");
        let pids = vec![current_pid];
        sys.refresh_processes(ProcessesToUpdate::Some(&pids), true);

        // Log resource usage for the current Rust process.
        log_rust_usage(&sys);

        let mut batch = Vec::new(); // Pre-allocate empty batch

        {
            let mut locked_batch = message_batch.lock().await;
            mem::swap(&mut batch, &mut *locked_batch); // Swap buffers instead of cloning
        }

        if !batch.is_empty() {
            last_message_time = Instant::now();
            send_messages_to_backend(&client, &args.video_id, &batch).await?;
        }
        //  else if Instant::now().duration_since(last_message_time) >= inactivity_limit {
        //     #[cfg(debug_assertions)]
        //     println!("❗️ No messages received for 30 minutes. Exiting scraper loop.");
        //     break;
        // }

        let interval_messages = batch.len();
        total_messages.fetch_add(interval_messages, Ordering::SeqCst);

        if !is_running.load(Ordering::SeqCst) {
            break;
        }
        send_metrics(
            &client,
            &args.video_id,
            &skip_langs,
            ScraperStatus::Running,
            &created_at,
            Some(&video_title),
            Some(&channel_name),
            interval_messages,
            total_messages.load(Ordering::SeqCst),
        )
        .await?;

        let time_since_last_message = Instant::now().duration_since(last_message_time);

        // After 15 minutes of inactivity, check via HTTP request if chat is really dead
        if time_since_last_message >= check_chat_deadline && Instant::now().duration_since(last_chat_check) >= Duration::from_secs(60) {
            if has_live_chat_ended(&client, &iframe_url).await? {
                #[cfg(debug_assertions)]
                println!("🔴 Live chat is unavailable (detected via HTTP request). Stopping.");
                break;
            }
            last_chat_check = Instant::now();
        }
    
        // If no activity for 30 minutes, exit without checking further
        if time_since_last_message >= inactivity_limit {
            #[cfg(debug_assertions)]
            println!("⏳ No messages for 30 minutes. Exiting.");
            break;
        }
    

        sleep(Duration::from_secs(10)).await;

        if !is_running.load(Ordering::SeqCst) {
            break;
        }
    }

    send_metrics(
        &client,
        &args.video_id,
        &skip_langs,
        ScraperStatus::Completed,
        &created_at,
        Some(&video_title),
        Some(&channel_name),
        0,
        total_messages.load(Ordering::SeqCst),
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
    use tokio::signal::unix::{signal, SignalKind};

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
    use tokio::{io::{self, AsyncBufReadExt, BufReader}, signal::windows};

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
) -> Result<()> {
    client
        .post("http://localhost:8080/scrapers/updateMetrics")
        .json(&json!({
            "videoTitle": video_title,
            "channelName": channel_name,
            "videoId": video_id,
            "skipLangs": skip_langs,
            "status": status.as_str(),
            "createdAt": created_at,
            "messagesInLastInterval": messages_in_last_interval,
            "totalMessages": total_messages,
        }))
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

    BrowserConfig::builder()
        .no_sandbox()
        .chrome_executable(default_executable(DetectionOptions{msedge: false, unstable: true}).unwrap())
        // .disable_cache()
        .headless_mode(chromiumoxide::browser::HeadlessMode::True)
        .args([
            "--no-startup-window",
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
    Ok(())
}

// async fn handle_chat_events(page: Page, counter: Arc<AtomicUsize>) -> Result<()> {
async fn handle_chat_events(page: Page, message_batch: Arc<Mutex<Vec<ChatMessage>>>) -> Result<()> {
    let mut events = page
        .event_listener::<chromiumoxide::cdp::js_protocol::runtime::EventBindingCalled>()
        .await?;

    while let Some(event) = events.next().await {
        if event.name == "rustChatHandler" {
            let parts: Vec<&str> = event.payload.split('\t').collect();
            if parts.len() == 2 {
                let username = parts[0].to_string();
                let message = parts[1].to_string();

                let chat_msg = ChatMessage { username, message };

                message_batch.lock().await.push(chat_msg);
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

*/