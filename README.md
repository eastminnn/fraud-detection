# fraud-detection

로그인 이벤트를 실시간으로 집계해 브루트포스 공격을 탐지하고 계정을 차단하는 시스템.

동일 계정에 짧은 시간 요청이 몰리는 상황에서도 판정이 어긋나지 않도록,
집계와 차단 판정을 Redis 에서 원자적으로 처리한다.

## 개요

로그인 시도를 기록하고, 슬라이딩 윈도우 안의 실패 횟수가 임계치를 넘으면 계정을 차단한다.

| | |
|---|---|
| 탐지 규칙 | 동일 계정 **5분 이내 로그인 실패 10회** |
| 차단 방식 | 계정 상태를 `BLOCKED` 로 변경. 비밀번호가 일치해도 거부 |
| 집계 저장소 | Redis Sorted Set (슬라이딩 윈도우) |
| 판정 원자성 | Lua 스크립트 — 윈도우 갱신·집계·중복 차단 방지를 한 단위로 실행 |

동시 요청 20건을 같은 계정에 보내도 차단 처리는 정확히 한 번만 실행된다.

### 개발 환경

실서비스에서 흔히 발생하는 자원 제약을 재현하기 위해 컨테이너 리소스를 제한한 상태로 개발한다.

| 항목 | 사양 |
|---|---|
| 기기 | MacBook Air 15" (M3, 2024), 메모리 16GB |
| 컨테이너 런타임 | Docker Desktop — CPU 4코어 / 메모리 6~8GB |
| PostgreSQL | 메모리 512MB 제한 |
| Redis | 메모리 256MB 제한, `maxmemory 192mb` |

### 목표 처리량

위 환경을 기준으로 설정한 설계 목표다. 측정 결과가 아니며, 부하 테스트로 검증할 대상이다.

| 시나리오 | 목표 | 목적 |
|---|---|---|
| 평시 | 30~50 TPS | 지연 없이 처리되는 기준선 |
| 스파이크 | 200~500 TPS | 처리 지연과 커넥션 풀 포화 유도 |
| 집중 공격 | 동일 계정 초당 100건 | 슬라이딩 윈도우 카운터의 경합 재현 |

## 아키텍처

```
POST /api/login
      │
      ▼
 LoginService ─── ① 계정 상태 확인 (BLOCKED 면 즉시 거부)
      │
      ├───────── ② 비밀번호 검증
      │
      ├───────── ③ login_attempts 저장  ──────────▶  PostgreSQL
      │                                                (감사 기록)
      ▼
 BruteForceDetector
      │
      ├───────── ④ LoginAttemptCounter ──────────▶  Redis
      │              (Lua 스크립트, 원자적)           login:fail:{username}
      │                                              login:blocked:{username}
      │
      └───────── ⑤ 임계치 초과 시
                     계정 BLOCKED + 탐지 기록  ────▶  PostgreSQL
```

집계는 Redis, 감사 기록과 탐지 결과는 PostgreSQL 에 남긴다.
판정에 필요한 데이터는 전부 Redis 안에 있어 요청 경로에서 DB 집계 쿼리가 발생하지 않는다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 / 프레임워크 | Java 17, Spring Boot 4.1.1 |
| 데이터베이스 | PostgreSQL 17, Spring Data JPA, Flyway |
| 집계 저장소 | Redis 8, Spring Data Redis (Lettuce), Lua |
| 빌드 | Gradle |
| 테스트 | JUnit 5, AssertJ, Testcontainers |
| 인프라 | Docker Compose |
| CI | GitHub Actions |

## 실행 방법

### 인프라 기동

```bash
docker compose up -d
```

PostgreSQL(5432)과 Redis(6379)가 뜬다. RedisInsight 를 함께 띄우려면 프로파일을 지정한다.

```bash
docker compose --profile observability up -d     # RedisInsight → localhost:5540
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

스키마는 Flyway 가 적용한다.

### Redis 상태 확인

```bash
docker compose exec redis redis-cli

> ZRANGE login:fail:<username> 0 -1 WITHSCORES   # 윈도우 안의 실패 기록
> ZCARD  login:fail:<username>                   # 실패 횟수
> TTL    login:fail:<username>                   # 키 만료까지 남은 시간
```

## API

### `POST /api/login`

```json
{ "username": "minsu", "password": "password123" }
```

| 응답 | 조건 |
|---|---|
| `200 OK` | 로그인 성공. `{ "username": "minsu" }` |
| `400 INVALID_INPUT_VALUE` | 아이디 또는 비밀번호 누락 |
| `401 LOGIN_FAILED` | 아이디 불일치 또는 비밀번호 불일치 |
| `403 ACCOUNT_BLOCKED` | 차단된 계정 |

계정이 존재하지 않는 경우와 비밀번호가 틀린 경우를 구분하지 않고 모두 `LOGIN_FAILED` 로 응답한다.
구분하면 응답만으로 어떤 계정이 실제로 존재하는지 알아낼 수 있다.

## 테스트

```bash
./gradlew test
```

Testcontainers 가 PostgreSQL 과 Redis 컨테이너를 띄우므로 Docker 가 실행 중이어야 한다.
마이그레이션과 JPA 매핑, Lua 스크립트가 실제 엔진을 상대로 검증된다.

동시성 검증은 `CountDownLatch` 로 스레드를 대기시켰다가 한 번에 출발시켜 경합을 재현한다.
스레드를 순차로 생성하면 첫 번째가 끝난 뒤 마지막이 시작될 수 있어 경합이 나타나지 않는다.
