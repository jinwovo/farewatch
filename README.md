# farewatch

> 원하는 날짜·도시의 항공권 최저가를 **매시간 대신 지켜보다가**, 조건이 맞으면 **알림 → 최저가 사이트로 바로 이동**시켜 주는 가격 감시 메타서치.

[![ci](https://github.com/jinwovo/farewatch/actions/workflows/ci.yml/badge.svg)](https://github.com/jinwovo/farewatch/actions/workflows/ci.yml)
[![android](https://github.com/jinwovo/farewatch/actions/workflows/android.yml/badge.svg)](https://github.com/jinwovo/farewatch/actions/workflows/android.yml)
&nbsp; Java 21 · Spring Boot 4.1 · PostgreSQL · Redis · Next.js · Kotlin(Compose)

<p align="center">
  <img src="docs/demo/dashboard.png" width="760" alt="홈 대시보드 — 워치별 현재가·딜 스코어·스파크라인">
</p>
<p align="center"><sub>홈 대시보드 (실데이터 라이브 캡처) — 워치마다 현재가 · 역대최저 대비 · 딜 스코어 · 30일 스파크라인 · 일시정지, API 호출은 <code>/api/watches/summary</code> 한 번.</sub></p>

여행을 한 달 전쯤 계획하면 그 사이 가격은 하루에도 몇 번씩 출렁인다. farewatch는 **노선 + 유연한 날짜 + 알림 규칙**을 등록받아 여러 소스를 매시간 폴링하고, 규칙이 발화하면 푸시/이메일로 알린 뒤 **그 가격을 파는 사이트로 딥링크**한다. 예약·결제는 그 사이트의 몫 — 스카이스캐너와 같은 메타서치 모델이다.

## 핵심 근육 (What this proves)

단순 크롤러가 아니라 **분산 가격-감시 엔진**이 심장:

- **중복 없는 분산 스케줄링** — N개 인스턴스의 시간당 폴링을 Redis 분산락 → Redis Streams 큐 샤딩으로 *정확히 한 번* 실행
- **큐 장애복구** — 워커가 죽어도 손실 0: XPENDING+XCLAIM으로 reclaim, 반복 실패 잡은 DLQ 격리
- **외부 API 보호** — 소스별 토큰버킷 레이트리밋 + 서킷브레이커(직접 구현) + 적응형 폴링(예산 내 고가치 워치 우선)
- **멀티소스 정규화** — GDS(Amadeus) · 애그리게이터(Travelpayouts) · 시뮬레이터를 하나의 `FarePriceProvider`로 합류
- **멱등 알림** — 트랜잭션 아웃박스 + `dedup_key`, 같은 하락을 두 번 쏘지 않음 · 재시도→FAILED
- **결정 레이어** — 백분위·추세·변동성·D-day 기반 **매수 신호 + 0–100 딜 스코어** (ML 없이 숫자로 방어 가능)
- **에러요금 이상탐지** — 새 최저가가 노선 이력 대비 z-score ≤ −2.5면 🔥 *에러요금 의심* 우선 알림

## 스크린샷

**웹** — 스타크 화이트 + DM Sans + 블랙 필 CTA + 코랄 "현재 최저가" 카드 하나. 차트·캘린더·히트맵 전부 의존성 없는 SVG.

<p align="center">
  <img src="docs/demo/weather.png" width="49%" alt="워치 상세 — 최저가·매수신호·알림·날씨·히트맵">
  <img src="docs/demo/home.png" width="49%" alt="워치 만들기 — 공항 자동완성·유연 날짜·시간대">
</p>
<p align="center"><sub>워치 상세 (코랄 최저가 · 매수신호 · 알림 내역 · 도착지 날씨 · 히트맵) &nbsp;|&nbsp; 워치 만들기 (스카이스캐너식 검색바)</sub></p>

<p align="center">
  <img src="docs/demo/alert-feed.png" width="90%" alt="전역 알림 피드">
</p>
<p align="center"><sub>전역 알림 피드 — 목표가 도달 · 새 최저가 · 🔥에러요금 의심이 규칙 칩으로 구분되어 쌓인다 (<code>/api/alerts</code>)</sub></p>

<details>
<summary><b>검색 UX 더 보기</b> — 공항 자동완성 · 유연 날짜 캘린더 · 한국어 검색 · 왕복</summary>
<br>
<p align="center">
  <img src="docs/demo/autocomplete.png" width="49%" alt="공항 자동완성">
  <img src="docs/demo/calendar.png" width="49%" alt="유연 날짜 캘린더">
</p>
<p align="center">
  <img src="docs/demo/korean-search.png" width="49%" alt="한국어 공항 검색">
  <img src="docs/demo/roundtrip.png" width="49%" alt="왕복 — 두 개의 시간대">
</p>
<p align="center"><sub>자동완성(근처공항·거리, OurAirports 실데이터) · 2개월 캘린더 · 전 세계 한국어 검색(Wikidata+큐레이션) · 왕복(가는/오는 편 시간대)</sub></p>
</details>

**Android (Kotlin · Jetpack Compose)** — 같은 `/api`를 소비하는 네이티브 앱, 웹과 완전 파리티. MuMu(arm64) 실기기에서 실데이터로 검증.

<p align="center">
  <img src="docs/demo/android-dashboard.png" width="24%" alt="Android 홈 대시보드">
  <img src="docs/demo/android-detail.png" width="24%" alt="Android 워치 상세">
  <img src="docs/demo/android-search.png" width="24%" alt="Android 검색/생성">
  <img src="docs/demo/android-alerts.png" width="24%" alt="Android 알림 피드">
</p>
<p align="center"><sub>홈 대시보드(딜점수 정렬·일시정지) · 상세(코랄 카드·차트) · 검색/생성 · 알림 피드+조건 편집</sub></p>

<details>
<summary><b>Android 더 보기</b> — 히트맵 · 한국어 자동완성 · 시간대/좌석 · 왕복</summary>
<br>
<p align="center">
  <img src="docs/demo/android-detail-2.png" width="24%" alt="히트맵">
  <img src="docs/demo/android-search-korean.png" width="24%" alt="한국어 자동완성">
  <img src="docs/demo/android-search-2.png" width="24%" alt="시간대·좌석">
  <img src="docs/demo/android-search-roundtrip.png" width="24%" alt="왕복">
</p>
</details>

**관측성** — 인스턴스를 2개 띄우면 "poll throughput by instance"에 두 계열이 쌓인다. *같은 워치를 중복 폴하지 않으면서* 처리량이 합산되는, 분산 스케줄의 시각적 증명.

<p align="center">
  <img src="docs/demo/grafana.png" width="90%" alt="Grafana 16패널 대시보드 — 2 인스턴스 분산 drain">
</p>
<p align="center"><sub>Grafana 16패널 라이브 캡처 (instances=2 · 소스 p95 · 에러요금 · 알림 발송 · JVM) — <code>.\run-cluster.ps1 -Instances 2 -Simulator</code>로 재현 · <a href="docs/adr/0004-observability.md">ADR-0004</a></sub></p>

## 설계 (Design)

```mermaid
flowchart LR
    U["사용자"] -->|워치 생성| API["farewatch API<br/>(Spring Boot :8101)"]
    API --> PG[("PostgreSQL :5435<br/>watch · price_point(시계열) · alert")]
    PG -.->|due 스캔| SWEEP
    SWEEP["시간당 스윕<br/>ShedLock(MVP)→Redis Streams 샤딩"] -->|due 워치| WORKER["폴 워커"]
    WORKER -->|토큰버킷+서킷브레이커| AGG["FarePriceProvider<br/>애그리게이터 (정규화·최저가 채택)"]
    AGG --> AM["Amadeus (GDS)"]
    AGG --> TP["Travelpayouts"]
    AGG --> SC["LCC 스크래퍼"]
    AGG --> SIM["Simulator"]
    WORKER -->|규칙 발화?| DET["변화 감지"]
    DET -->|멱등 dedup + 재시도| NOTIF["Notifier"]
    NOTIF --> FCM["FCM 푸시 → Android(Compose)"]
    NOTIF --> MAIL["Email"]
    PG --> WEB["웹 (Next.js)<br/>대시보드·알림 피드·차트"]
```

## 검증 (Verification)

> 말이 아니라 실행으로 증명 — 전 항목 Testcontainers(실 PostgreSQL·Redis) 통합 테스트 + 라이브 확인.

- [x] 다른 인스턴스가 락 보유 시 **중복 폴링 0** (Redis 분산락)
- [x] 컨슈머그룹 **샤딩** — 두 워커가 잡 분할, 각 워치 정확히 1회 폴
- [x] **장애복구** — 죽은 워커의 미-ack 잡을 다른 워커가 reclaim해 정확히 1회 폴 · 반복 실패 잡은 DLQ 격리
- [x] **토큰버킷** 버스트→거부 · **서킷브레이커** 상태머신 · **적응형 폴링**(예산 내 고가치 우선)
- [x] 알림 **멀티채널 발송** — 아웃박스 · 멱등 dedup · 재시도→FAILED
- [x] **알림 규칙** — 목표가 이하가 첫 폴에 발화해 전역 피드에 노출 · PATCH 규칙 편집 + 파라미터 검증
- [x] **대시보드 요약** — 가격·신호·스파크라인 단일 호출 · 일시정지/재개
- [x] 폴 **부하 테스트** — k6 30VU: ~145 polls/s · p95 211ms · 0% 에러 (`load/k6-poll.js`)
- [x] **라이브** — 실 Travelpayouts 폴로 새 최저가 알림 발화(EMAIL/PUSH SENT), 목표가 규칙은 `newLow:false`에도 발화하는 것까지 확인

## 스택 (Stack)

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21 · Spring Boot 4.1 (Jackson 3) · Gradle |
| DB | PostgreSQL 16 + Flyway · `ddl-auto: none` · `price_point` 시계열 + 일별 롤업 리텐션 |
| 스케줄/큐 | 스윕 하트비트 + Redis 분산락 · Redis Streams 컨슈머그룹 샤딩 + XCLAIM/DLQ |
| 회복탄력성 | 토큰버킷 레이트리밋 · 서킷브레이커 (둘 다 직접 구현, Redis Lua) |
| 외부 소스 | Amadeus · Travelpayouts · Open-Meteo(날씨) — 전부 config-gated |
| 프론트 | 웹 Next.js(App Router·TS strict) · Android Kotlin + Jetpack Compose |
| 관측 | Micrometer → Prometheus `:9096` + Grafana `:3004` (코드 프로비저닝 16패널) |
| 테스트 | Testcontainers (`@ServiceConnection`) · GitHub Actions CI (backend + android) |

## 가격 소스 전략 (Sources)

| 소스 | 잡는 재고 풀 |
|---|---|
| Amadeus Self-Service | 레거시 항공사 (GDS) |
| Travelpayouts (Aviasales) | 폭넓은 캐시 최저가 + 가격이력 |
| LCC 스크래퍼 (옵션·기본 off) | GDS가 못 잡는 LCC 직판 |
| Simulator | 합성 가격워크 (부하·상시 데모) |

> ⚠️ **정직성 노트:** 공개 항공권 가격 API는 사실상 없다(스카이스캐너 = 파트너 전용, 구글 플라이트 = API 없음). 그래서 **합법적으로 접근 가능한 상보적 소스**를 `FarePriceProvider`로 합류하고, 부하테스트·데모는 Simulator로 돌린다. 엔진(스케줄·레이트리밋·dedup·알림)은 소스와 무관하게 동일하다. → [ADR-0002](docs/adr/0002-multi-source-fare-aggregator.md)

## Quickstart

```bash
docker compose up -d                  # PostgreSQL·Redis + Prometheus·Grafana
./gradlew bootRun                     # API :8101
cd web && npm install && npm run dev  # 웹 :3005 (/api/* → :8101 프록시)
```

```powershell
.\run.ps1                                    # 실소스(.env 토큰 주입) + 메모리 바운드 jar 실행
.\run-cluster.ps1 -Instances 2 -Simulator    # 2 인스턴스 분산 drain을 Grafana(:3004)로 관전
```

## API

| 엔드포인트 | 설명 |
|---|---|
| `POST /api/watches` · `GET /api/watches` | 워치 생성 · 목록 |
| `GET /api/watches/summary` | 홈 대시보드 페이로드 — 현재가·매수신호·30일 스파크라인 (배치 쿼리) |
| `PATCH /api/watches/{id}` | 일시정지/재개 · 알림 규칙 편집(목표가/급락%, 서버 검증) |
| `GET /api/watches/{id}/prices · calendar · alerts · weather · signal` | 시계열 · 날짜별 최저가 · 알림 내역 · 날씨 · 매수신호 |
| `POST /api/watches/{id}/poll` | 즉시 폴 (소스 합류 → 최저가 적재 → 규칙 평가) |
| `GET /api/alerts` | 전역 알림 피드 (노선·발송상태·딥링크 포함) |
| `GET /api/airports?q=` · `/{iata}/nearby` | 공항 자동완성(한국어 포함) · 근처 공항 |

## 로드맵

| 단계 | 한 줄 요약 |
|:--:|---|
| ✅ P0–P1 | 스캐폴드(Flyway·Testcontainers·CI·ADR) · 워치 CRUD + 소스 추상화 + 시계열 |
| ✅ P2 | 분산 스케줄 — 시간당 스윕 + Redis 분산락, 중복 폴링 0 |
| ✅ P3 | 알림 — 트랜잭션 아웃박스 · 멀티채널 · 멱등 dedup · 재시도 |
| ✅ P4 | 스케일 — Streams 샤딩 · 토큰버킷 · 서킷브레이커 · 적응형 폴링 · k6 145 polls/s |
| ✅ P5 | 도착지 날씨 — Open-Meteo 실예보(D-16) ↔ 평년값 크로스오버 |
| ✅ 실소스 | Amadeus(OAuth2) · Travelpayouts(Aviasales 실링크) — config-gated |
| ✅ Android | Compose+Retrofit 네이티브 앱, 웹 완전 파리티 (MuMu 실기기 검증) |
| ✅ 차별점 | 매수 신호+딜 스코어 · 에러요금 z-score 탐지 · 큐 장애복구(XCLAIM+DLQ) |
| ✅ 운영 | 가격이력 리텐션(일별 롤업+90d purge) · Micrometer 전 핫패스 · 런처 스크립트 |
| ✅ 관측성 | Prometheus+Grafana 16패널 · 2-인스턴스 분산 drain 라이브 캡처 |
| ✅ 대시보드 UX | summary 단일 호출 카드(가격·신호·스파크라인) · 정렬 · 일시정지 — 웹·Android |
| ✅ 알림 UX | 규칙 3종 편집(새 최저가/목표가/급락%) · 전역 알림 피드 — 웹·Android |
| ⬜ 남은 것 | FCM 실수신(Firebase 설정) · 웹 데모 GIF |

## ADR

- [ADR-0001 — ADR 시작 & 스택/포트](docs/adr/0001-record-architecture-decisions.md)
- [ADR-0002 — 멀티소스 운임 애그리게이터 & 소스 전략](docs/adr/0002-multi-source-fare-aggregator.md)
- [ADR-0003 — 분산 폴링: ShedLock → Redis Streams](docs/adr/0003-distributed-polling.md)
- [ADR-0004 — 관측성: Prometheus + Grafana](docs/adr/0004-observability.md)
