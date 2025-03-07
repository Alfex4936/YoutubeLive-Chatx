# yt-chatx

Youtube Live 채팅을 API 없이 실시간 읽기

진행 중...

## 개발 스택
- Redis - Realtime statistics
- Playwright - Chrome mock
- Spring boot 3.4 (VT on) - API server
- Java 23
- Postgres 17 - Profanity, logs etc
- Websocket (SockJS) - Realtime get

## 기능
- [x] Youtube Live 채팅 읽기
- [x] 실시간 통계
  - [x] 채팅 수 (x 초당 + 전체 + 평균)
  - [x] 욕설 수 (x 초당)
  - [x] 키워드 랭킹
  - [x] 언어 감지 (랭킹에서 제외할 언어)
  - [ ] 가장 많이 떠든 사람
  - [ ] 유니크 유저 수

## 테스트

![actuator](https://github.com/user-attachments/assets/57bbf7a0-d88f-406f-993a-366abcc7a5e2)

![dashboard](https://github.com/user-attachments/assets/63891103-6d33-45e7-a172-3c5265bd8b1b)

![Image](https://github.com/user-attachments/assets/253585cc-5cf1-42bc-8b45-7729e851ad4b)