# ADR-0004 — 관측성: Prometheus + Grafana로 분산 스윕을 눈으로 증명

- 상태: Accepted — 관측성 고도화 구현 완료
- 날짜: 2026-07-10

## 맥락
P4에서 분산 스윕(Redis Streams 샤딩)·적응형 폴링·서킷브레이커·레이트리밋을 만들었고, 운영 고도화에서 Micrometer로 모든 핫패스에 메트릭을 심어 `/actuator/prometheus`로 노출했다. 그런데 *"N개 인스턴스가 중복 없이 폴을 나눠 처리한다"*, *"소스가 느려지면 p95가 튄다"*, *"에러요금이 떴다"* 같은 시스템의 이야기가 텍스트 메트릭으로만 있으면 아무도 읽지 않는다. 이 프로젝트의 헤드라인 근육(분산 스케줄러)을 **시각적으로** 증명할 레이어가 필요하다 — 레지스트리에도 farewatch의 Prometheus/Grafana는 "예약·미구축"으로 남아 있었다.

## 결정
- **Prometheus(`v2.54.1`) + Grafana(`11.2.0`)를 `docker-compose.yml`에 추가.** Grafana는 datasource + 대시보드를 **코드로 프로비저닝**(`observability/`), 익명 read-only(로그인 없이 `http://localhost:3004`). 형제 프로젝트(dispatch·aegis·weave)와 동일 패턴 → 포트폴리오 전반의 관측성 일관성.
- **앱은 호스트에서 실행**(`run.ps1`/`java -jar`)하므로 Prometheus는 컨테이너 경계를 넘어 `host.docker.internal`로 스크랩(`extra_hosts: host-gateway`). 스크랩 타겟에 `:8101`·`:8102`를 **둘 다** 등록 → 2번째 인스턴스를 띄우면 **설정 수정 없이** 인스턴스별 패널이 살아난다(내려가 있는 타겟은 `up=0`, "instances" 스탯은 `up==1`만 카운트해 단일 인스턴스도 깔끔).
- **p95에는 히스토그램 버킷이 필요**하다. Micrometer 타이머는 기본적으로 count+sum만 내보내므로 `management.metrics.distribution.percentiles-histogram`을 소스 호출 타이머(`farewatch.source.calls`)와 HTTP 타이머(`http.server.requests`)에 켰다 — 안 켜면 Grafana `histogram_quantile`이 No data.
- **대시보드는 farewatch의 이야기를 16패널로**: 인스턴스별 폴 처리량(분산 drain — 머니샷), 스윕 파이프라인(enqueue/drain/defer/recover), 소스별 호출·p95·스킵(서킷 open/레이트리밋), 알림·에러요금(🔥), 알림 발송(채널×결과), 리텐션 롤업/purge, JVM 힙·HTTP p95.
- **집계 주의**: 큐 깊이·DLQ 게이지는 **모든 인스턴스가 같은 Redis 스트림을 읽어** 인스턴스마다 값이 동일 → `max()`로 집계(중복 합산 방지). 인스턴스별 카운터는 `sum(rate(...)) by (instance)`.
- **멀티인스턴스 데모 런처** `run-cluster.ps1`(N개 인스턴스 + 관측성 스택) / `stop-cluster.ps1`. 인스턴스는 **순차 기동**(각 `/actuator/health` UP 확인 후 다음) — 공유 호스트에서 여러 JVM을 동시에 띄우면 서로 CPU를 굶겨 Spring 컨텍스트 초기화가 정체된다(라이브에서 확인).

## 대안
- **k3d 멀티팟** — 진짜 쿠버네티스지만 Windows-on-ARM 메모리 제약에서 무겁고, 분산 exactly-once는 이미 `QueueShardingIntegrationTest`로 증명됨. 호스트 JVM 멀티인스턴스가 같은 주장을 **가볍게 시각화**한다(대시보드는 `by (instance)`라 팟이든 프로세스든 동일).
- **Grafana Cloud / 외부 수집기** — 로컬 데모·CI 재현성·오프라인 원칙에 어긋난다. 컨테이너 스택이 self-contained.
- **부하를 실 Travelpayouts로** — 데모 처리량을 위해 실 API를 수백 번 때리는 건 쿼터 낭비. 오프라인·무제한인 **Simulator**를 켜서 처리량·지연·알림·에러요금을 전부 그려낸다(엔진은 소스와 무관하게 동일 — ADR-0002).

## 결과
`/actuator/prometheus`가 말로만 하던 이야기를 이제 대시보드가 보여준다. 인스턴스 2개를 띄우면 "sweep · poll throughput by instance"에 두 계열이 **쌓여** 중복 없이 처리량이 합산되는 게 보인다(라이브 캡처 `docs/demo/grafana.png`: instances=2, ~6 polls/s, 소스 p95, 에러요금 🔥, JVM 힙 인스턴스별 2계열). "10k 유저 OK?"에 대한 답 — 스코어러·백프레셔·서킷브레이커·큐 샤딩 — 이 한 화면에서 관측된다.
