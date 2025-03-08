// src/main.rs
use anyhow::{Context, Result};
use chromiumoxide::{Browser, BrowserConfig, Element, Page, handler::viewport::Viewport};
use chrono::Utc;
use clap::Parser;
use futures::StreamExt;
use reqwest::Client;
use serde_json::json;
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
use sysinfo::{MINIMUM_CPU_UPDATE_INTERVAL, Pid, ProcessesToUpdate, System, get_current_pid};
use tokio::{
    signal,
    sync::Mutex,
    time::{Duration, sleep},
};

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
async fn main() -> Result<()> {
    let args = Args::parse();
    let skip_langs: Vec<&str> = args
        .skip_langs
        .split(',')
        .filter(|s| !s.is_empty())
        .collect();

    let is_running = Arc::new(AtomicBool::new(true));
    tokio::spawn(handle_signals(Arc::clone(&is_running)));

    let client = Client::new();
    let created_at = Utc::now().to_rfc3339();
    let total_messages = Arc::new(AtomicUsize::new(0));

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
    )
    .await?;

    let (mut browser, mut handler) = Browser::launch(config_browser()).await?;
    tokio::spawn(async move { while handler.next().await.is_some() {} });

    //let chrome_pid: Option<Pid> = handler..().map(|pid| Pid::from(pid));

    let page = browser
        .new_page(format!("https://www.youtube.com/watch?v={}", args.video_id))
        .await?;
    page.wait_for_navigation_response().await?;

    wait_for_selector(&page, "iframe#chatframe").await?;
    let iframe_url = get_iframe_url(&page).await?;

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

    let message_counter = Arc::new(AtomicUsize::new(0));
    let no_message_counter = Arc::new(AtomicUsize::new(0));

    // tokio::spawn(handle_chat_events(iframe_page.clone(), Arc::clone(&message_counter)));
    let message_batch = Arc::new(Mutex::new(Vec::new()));
    tokio::spawn(handle_chat_events(
        iframe_page.clone(),
        Arc::clone(&message_batch),
    ));

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
    )
    .await?;

    // Create a single System instance to use throughout the lifetime of the scraper.
    let mut sys = System::new_all();

    loop {
        if !is_running.load(Ordering::SeqCst)
        // || scraper_should_stop(&iframe_page, &no_message_counter, &message_counter).await
        {
            break;
        }

        // Prepare the list of PIDs to update: always include the current process.
        let current_pid = get_current_pid().expect("Failed to get current PID");
        let pids = vec![current_pid];
        sys.refresh_processes(ProcessesToUpdate::Some(&pids), true);

        // Log resource usage for the current Rust process.
        log_rust_usage(&sys);

        let batch = {
            let mut locked_batch = message_batch.lock().await;
            let batch = locked_batch.clone();
            locked_batch.clear();
            batch
        };

        if !batch.is_empty() {
            send_messages_to_backend(&client, &args.video_id, &batch).await?;
        }

        let interval_messages = batch.len();
        total_messages.fetch_add(interval_messages, Ordering::SeqCst);

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

        sleep(Duration::from_secs(10)).await;
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
    let _ = browser.kill().await.unwrap();

    Ok(())
}

async fn handle_signals(is_running: Arc<AtomicBool>) {
    signal::ctrl_c().await.expect("Failed to handle Ctrl+C");
    is_running.store(false, Ordering::SeqCst);
    println!("Graceful shutdown triggered");
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
    BrowserConfig::builder()
        .no_sandbox()
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
    for _ in 0..10 {
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

async fn scraper_should_stop(
    page: &Page,
    no_msg_counter: &AtomicUsize,
    msg_counter: &AtomicUsize,
) -> bool {
    let current_url = page.url().await.unwrap_or_default().unwrap_or_default();
    if !current_url.starts_with("https://www.youtube.com/live_chat") {
        println!("❌ Live chat page changed! Stopping scraper.");
        return true;
    }
    if msg_counter.load(Ordering::SeqCst) == 0 {
        no_msg_counter.fetch_add(1, Ordering::SeqCst);
    } else {
        no_msg_counter.store(0, Ordering::SeqCst);
    }
    no_msg_counter.load(Ordering::SeqCst) >= 30
}

/// Logs resource usage for the current Rust process using sysinfo.
fn log_rust_usage(sys: &System) {
    let current_pid = get_current_pid().expect("Failed to get current PID");
    if let Some(process) = sys.process(current_pid) {
        println!(
            "💻 Rust Process - CPU: {:.2}%, Memory: {} KB",
            process.cpu_usage(),
            process.memory()
        );
    } else {
        println!("Could not retrieve Rust process info.");
    }
}
