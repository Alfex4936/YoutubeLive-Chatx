# gRPC API Documentation

This application now supports both REST and gRPC APIs for all major operations.

## gRPC Server Configuration

- **Port**: 9090
- **Reflection**: Enabled (for testing with tools like grpcurl, BloomRPC, or Postman)
- **Max Message Size**: 16MB

## Available gRPC Services

### 1. ScraperService

Manages YouTube chat scrapers.

**Methods:**
- `StartScraper(StartScraperRequest) → StartScraperResponse`
- `StopScraper(StopScraperRequest) → StopScraperResponse`  
- `GetScraperStatus(GetScraperStatusRequest) → GetScraperStatusResponse`

**Example Usage:**
```bash
# Start a scraper
grpcurl -plaintext -d '{
  "video_id": "your-video-id",
  "skip_languages": ["ENGLISH", "SPANISH"]
}' localhost:9090 csw.youtube.chat.grpc.scraper.ScraperService/StartScraper

# Get scraper status
grpcurl -plaintext -d '{
  "video_id": "your-video-id"
}' localhost:9090 csw.youtube.chat.grpc.scraper.ScraperService/GetScraperStatus
```

### 2. StatisticsService

Provides comprehensive statistics and metrics.

**Methods:**
- `GetStatistics(GetStatisticsRequest) → GetStatisticsResponse`
- `UpdateMetrics(UpdateMetricsRequest) → UpdateMetricsResponse`
- `ProcessMessages(ProcessMessagesRequest) → ProcessMessagesResponse`
- `GetMessageGraph(GetMessageGraphRequest) → GetMessageGraphResponse`

**Example Usage:**
```bash
# Get statistics
grpcurl -plaintext -d '{
  "video_id": "your-video-id"
}' localhost:9090 csw.youtube.chat.grpc.statistics.StatisticsService/GetStatistics
```

### 3. AiService

Handles AI-powered chat analysis.

**Methods:**
- `SummarizeChat(SummarizeChatRequest) → SummarizeChatResponse`

**Example Usage:**
```bash
# Summarize chat
grpcurl -plaintext -d '{
  "video_id": "your-video-id",
  "language": "English"
}' localhost:9090 csw.youtube.chat.grpc.ai.AiService/SummarizeChat
```

### 4. UserService

User management operations.

**Methods:**
- `GetUserProfile(GetUserProfileRequest) → GetUserProfileResponse`

**Example Usage:**
```bash
# Get user profile
grpcurl -plaintext -d '{
  "user_id": 123
}' localhost:9090 csw.youtube.chat.grpc.user.UserService/GetUserProfile
```

## Testing gRPC Services

### Using grpcurl

1. Install grpcurl:
```bash
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

2. List available services:
```bash
grpcurl -plaintext localhost:9090 list
```

3. Describe a service:
```bash
grpcurl -plaintext localhost:9090 describe csw.youtube.chat.grpc.scraper.ScraperService
```

### Using BloomRPC or Postman

1. Connect to `localhost:9090`
2. Import the proto definitions from `src/main/proto/`
3. Use the reflection feature to auto-discover services

## Backward Compatibility

All existing REST endpoints remain fully functional:
- `/scrapers/start` → gRPC `ScraperService/StartScraper`
- `/scrapers/stop` → gRPC `ScraperService/StopScraper`
- `/scrapers/statistics` → gRPC `StatisticsService/GetStatistics`
- `/ai/summarize` → gRPC `AiService/SummarizeChat`

Both REST and gRPC share the same underlying service logic, ensuring consistent behavior.