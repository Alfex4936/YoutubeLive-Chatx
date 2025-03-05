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
  - [ ] 가장 많이 떠든 사람
  - [ ] 유니크 유저 수

## 테스트

![actuator](https://github.com/user-attachments/assets/3fd33db2-0ffb-46b1-b119-0be67bf6c161)

![dashboard](https://github.com/user-attachments/assets/384a4f97-9d38-461f-8687-d35f5be50fdb)

![Image](https://github.com/user-attachments/assets/253585cc-5cf1-42bc-8b45-7729e851ad4b)