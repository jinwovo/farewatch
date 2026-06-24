# ADR-0003 — 분산 폴링 스케줄링: ShedLock → Redis Streams 샤딩

- 상태: Accepted — P2 구현 완료 (P4 예정)
- 날짜: 2026-06-24

## 맥락
활성 워치를 **매시간(또는 워치별 주기)** 폴링해야 한다. 앱을 2개 이상 인스턴스로 띄우면 같은 워치를 **중복 폴링**할 위험이 있고, 외부 소스는 레이트리밋이 있어 중복은 곧 쿼터 낭비·차단이다. "정확히 한 번(혹은 최대 한 번) 실행"이 필요하다 — 레지스트리의 "스케줄러/잡큐(분산락·exactly-once)" 근육.

## 결정 (단계적)
- **P2 (구현됨): 직접 구현한 Redis 분산락.** ShedLock 대신 `SET key token NX PX ttl`(원자적 획득) + **Lua compare-and-delete**(자기 락만 해제)로 `RedisDistributedLock`을 직접 구현. `@Scheduled`(기본 매시간) 스윕이 락을 **못 잡으면 폴링 없이 skip** → "다른 인스턴스가 잡고 있으면 중복 폴링 0". ShedLock 미사용 이유: Boot 4 / Spring 7 서드파티 호환 리스크 회피 + 락 프리미티브를 직접 증명(포트폴리오 가치). 검증: `SweepLockIntegrationTest`(실 Redis) — 상호배제 · 외부 토큰 해제 불가 · 락 보유 중 스윕 중복폴링 0.
- **P4 (스케일): Redis Streams 잡 샤딩.** 스윕은 due 워치를 스트림에 enqueue만 하고, N개 워커가 컨슈머 그룹으로 분산 소비(수평 확장 + ack/재시도로 exactly-once 근사). 외부 호출 앞단에 **토큰버킷 레이트리밋 + resilience4j 서킷브레이커**로 백프레셔.
- due 스캔은 `watch.next_poll_at` 부분 인덱스(`WHERE active`)로 싸게 처리.

## 대안
- **Kafka 기반 잡큐** — 이미 포트폴리오에서 3회 사용(realtime / order-payment / recall). 새 근육이 아니고 무거워서 **Redis Streams** 선택.
- **DB 비관적 락**(`SELECT … FOR UPDATE SKIP LOCKED`) — 워커 풀엔 유효하나, 스케줄 트리거 중복 방지는 ShedLock이 더 간결.

## 결과
MVP는 단순(ShedLock)하게 "중복 0"을 증명하고, 스케일 단계에서 큐 샤딩으로 수평 확장과 레이트리밋 백프레셔를 보여준다. 소스가 크롤링이든 API든 이 엔진은 동일하게 동작한다(→ ADR-0002).
