# ADR-0001 — 아키텍처 결정 기록(ADR) 시작 & 기본 스택

- 상태: Accepted
- 날짜: 2026-06-24

## 맥락
`farewatch`는 포트폴리오의 다음 프로젝트로, 기존 프로젝트들(realtime-messaging / order-payment / social-feed / platform / recall / dispatch)과 **포트·컨테이너·주제가 겹치지 않게** 설계한다. 새로 증명하려는 근육은 **중복 없는 분산 스케줄링(스케줄러/잡큐) + 멱등 멀티채널 알림**이며, 부수적으로 **멀티소스 외부 API 정규화 + 소스별 레이트리밋/서킷브레이커**다. (레지스트리 7번 표의 "스케줄러/잡큐"·"알림 시스템" 칸을 정조준.)

## 결정
- **언어/프레임워크:** Java 21 · Spring Boot 4.1.0 (Jackson 3 — `tools.jackson.*`) · Gradle(Groovy, wrapper)
- **그룹/패키지:** `com.portfolio` / `com.portfolio.farewatch`
- **DB:** PostgreSQL 16 (`postgres:16-alpine`) + Flyway 로 스키마 소유(`ddl-auto: none`), `open-in-view: false`. 가격 이력은 `price_point` 시계열로 적재.
- **PK 전략:** **대리키 UUID** 만 사용. `@IdClass` 복합키 금지(Hibernate 7 / Boot 4.1 SessionFactory 부트스트랩 무한루프 이슈). 중복 워치는 surrogate id + `UNIQUE` 제약으로 차단.
- **캐시/락/큐:** Redis 7 (P2부터) — ShedLock 분산락 → Redis Streams 잡 샤딩, 토큰버킷 레이트리밋.
- **관측:** Micrometer + `/actuator/prometheus`.
- **테스트:** Testcontainers(`@ServiceConnection`).
- **포트(레지스트리 할당):** 앱 **8101** · PostgreSQL **5435** · Redis **6381** · 웹 **3005** · Prometheus **9092** · Grafana **3004**.
- **컨테이너 prefix:** `fw-`.
- **제품 범위:** 추적 + 알림 + **딥링크**(메타서치). 결제·발권은 범위 밖(→ ADR-0002).

## 결과
신규 자원이 기존 포트폴리오와 충돌하지 않고, 공통 컨벤션(Flyway·Testcontainers·멀티스테이지 Dockerfile·GitHub Actions·README 포맷)을 그대로 재사용한다.
