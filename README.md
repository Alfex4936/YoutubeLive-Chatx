# YTChatX

![frontend_demo3-1](https://github.com/user-attachments/assets/5a7957fb-c1e8-404b-92be-fc68119d5196)

<table>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/335ea176-7745-41d9-b388-44a3d281b377" width="600"></td>
    <td><img src="https://github.com/user-attachments/assets/c8c1d265-61e5-4459-a53e-bc0fe34385a1" width="600"></td>
  </tr>
  <tr>
    <td>Korean</td>
    <td>English</td>
  </tr>
</table>

YouTube Live 채팅을 API 없이 **실시간**으로 읽어 분석하는 프로젝트

*Work in Progress...*

## 개발 스택 (Tech Stack)

- **Redis**  
  실시간 통계 제공을 위한 캐시 서버
- **Playwright**  
  Chrome 모킹 및 브라우저 자동화를 통한 데이터 수집
- **Spring Boot 3.4 (VT on)**  
  API 서버 구현
- **Java 23 lang**  
  최신 자바 기능 활용
- **Postgres 17**  
  욕설 필터링, 로그 저장 등 데이터 관리
- **WebSocket (SockJS)**  
  실시간 데이터 전송
- **Rust lang**  
  간단한 Scraper 작업 프로세스 분리
- **ReactJS**  
  간단한 Frontend


## 기능 (Features)

- [x] **YouTube Live 채팅 읽기**  
  API 없이 라이브 채팅 데이터를 수집
- [x] **실시간 통계 제공**
  - 채팅 수 (초당, 전체, 평균)
  - 욕설 수 (초당)
  - 키워드 랭킹 (한국어일 경우 KOMORAN?)
  - 언어 감지 (특정 언어는 랭킹에서 제외)
  - 언어 통계 (top 3 언어 %)
  - 가장 많이 채팅한 사용자
  - [ ] 고유 사용자 수 집계
  - [ ] 그래프 저장 (지난 방송과 비교, 채팅 트렌드 비교)
    - [x] 30분 동안 메시지 수 차트 그래프
  - [ ] 감정 분석 (긍정 / 부정 / 중립) - local llama3-8b?
  - [ ] 최근 채팅 AI 요약
- [ ] 소셜 로그인 (OAuth2)

## 테스트 (Testing)

### Actuator 화면
![actuator](https://github.com/user-attachments/assets/57bbf7a0-d88f-406f-993a-366abcc7a5e2)

### Dashboard 화면
![dashboard](https://github.com/user-attachments/assets/63891103-6d33-45e7-a172-3c5265bd8b1b)

### 기타 스크린샷
![Image](https://github.com/user-attachments/assets/253585cc-5cf1-42bc-8b45-7729e851ad4b)

## 브라우저 방법

최고의 방법은 무엇일까?...

일단 Playwright는 thread-safe 하지 않아 같은 쓰레드가 작업해야한다.

시도한 방법들
1. Browser pool 을 미리 만들어서 사용 -> Object 없어짐
2. 각 VT를 저장해서 scraper 마다 VT 실행 -> 근본
3. Rust언어를 통해 정말 간단한 Scraper 프로세스만 만들기
   - JVM 없이 CPU, 메모리를 적게 먹으면서 크롬 실행 잘됨
   - 배치를 통해 자바 서버에 metrics를 보내줌