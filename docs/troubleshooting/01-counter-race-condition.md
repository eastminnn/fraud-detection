# 동시 요청에서 브루트포스 차단이 뚫리고 탐지 기록이 중복된 문제

## 증상

로그인 실패가 5분 내 10회를 넘으면 계정을 차단하도록 구현했다.
순차 요청으로는 정확히 10번째에 차단됐고 테스트도 통과했다.

동시 요청을 넣자 결과가 달라졌다. 실패 9건이 쌓인 계정에 로그인 실패 20건을 동시에 보냈더니,
차단 한 번에 남아야 할 탐지 기록이 **20건** 쌓였다.

```
Expected size: 1 but was: 20 in:
[com.eastminn.fraud.detection.FraudDetection@23258315,
 com.eastminn.fraud.detection.FraudDetection@61cb973d,
 ... 20건 ...]
```

## 재현

`CountDownLatch` 로 스레드 20개를 대기시켰다가 한 번에 출발시킨다.
스레드를 하나씩 만들면 첫 번째가 끝난 뒤 마지막이 시작될 수 있어 경합이 재현되지 않는다.

```java
@Test
void 동시에_임계치를_넘겨도_차단은_한_번만_기록된다() throws Exception {
	실패기록을_남긴다(9);

	int 동시요청수 = 20;
	ExecutorService pool = Executors.newFixedThreadPool(동시요청수);
	CountDownLatch 출발 = new CountDownLatch(1);
	CountDownLatch 완료 = new CountDownLatch(동시요청수);

	for (int i = 0; i < 동시요청수; i++) {
		pool.submit(() -> {
			try {
				출발.await();
				loginService.login(new LoginRequest("minsu", "wrong"), IP);
			} catch (Exception expected) {
			} finally {
				완료.countDown();
			}
		});
	}

	출발.countDown();
	완료.await(30, TimeUnit.SECONDS);
	pool.shutdown();

	assertThat(fraudDetectionRepository.findAll()).hasSize(1);
}
```

## 원인

판정이 **읽고 → 판단하고 → 쓰는** 세 단계로 나뉘어 있었다.

```java
long failures = loginAttemptRepository.countFailuresSince(username, since);   // ① 읽기

if (failures < THRESHOLD) {                                                   // ② 판단
	return;
}

userRepository.findByUsername(username).ifPresent(user -> {                    // ③ 쓰기
	user.block();
	userRepository.save(user);
});
fraudDetectionRepository.save(FraudDetection.bruteForce(...));
```

①과 ③ 사이에 다른 요청이 끼어든다. 20개 스레드가 모두 ①을 통과한 뒤에야 첫 번째 ③이 커밋됐다.
그래서 20개 전부 "내가 10번째" 라고 판단했다.

```
스레드 20개 동시 출발
  → 전부 COUNT 실행    → 전부 "9건이니 내가 10번째" 판단
  → 전부 user.block()  → 결과는 같으므로 문제없음
  → 전부 저장          → 탐지 기록 20건
```

`user.block()` 은 여러 번 호출해도 상태가 `BLOCKED` 로 같아 드러나지 않았다.
드러난 것은 탐지 기록 쪽이었다.

이 상태에서는 "몇 번 탐지되었는가" 통계가 요청이 몰릴수록 부풀려진다.
동일 계정 초당 100건을 가정한 목표 부하에서는 오차가 훨씬 커진다.

## 검토한 방법

**DB 행 잠금.** 계정 행에 `SELECT ... FOR UPDATE` 를 걸면 같은 계정의 판정이 직렬화되어 경합은 사라진다.
그러나 같은 계정 요청이 전부 줄을 서게 되어, **공격받는 계정일수록 느려진다.**
막아야 할 상황에서 응답이 가장 나빠지는 구조라 택하지 않았다.

**분산 락.** Redis 락을 잡고 판정하는 방법도 있다. 락 획득과 해제로 왕복이 두 번 늘고,
경합 시 대기가 생긴다. 락을 잡은 프로세스가 죽는 경우를 대비해 TTL 을 걸어야 하는데,
TTL 이 작업보다 짧으면 두 프로세스가 동시에 락을 가졌다고 믿는 문제가 새로 생긴다.
락 해제도 "내가 잡은 락인지" 확인 후 지워야 하므로 결국 원자적 실행이 필요하다.

락은 **끼어들지 못하게 막는** 도구다. 끼어들 틈 자체를 없앨 수 있다면 락은 필요하지 않다.

## 해결

집계를 Redis Sorted Set 으로 옮기고, 필요한 명령을 Lua 스크립트 하나로 묶었다.
Redis 는 스크립트를 단일 스레드로 끝까지 실행하므로 중간에 다른 요청이 들어오지 못한다.

```lua
-- KEYS[1] = login:fail:{username}      실패 기록 (Sorted Set)
-- KEYS[2] = login:blocked:{username}   차단 중복 방지

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])   -- 윈도우 밖 제거
redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])              -- 이번 실패 추가
redis.call('EXPIRE', KEYS[1], ARGV[4])

local count = redis.call('ZCARD', KEYS[1])
if count < tonumber(ARGV[5]) then
  return {count, 0}
end

-- 임계치에 도달한 요청이 여럿이어도 처음 하나만 1 을 받는다
if redis.call('SET', KEYS[2], '1', 'NX', 'EX', ARGV[4]) then
  return {count, 1}
end
return {count, 0}
```

집계가 원자적이어도 그 뒤 판정은 애플리케이션에서 각자 한다.
그래서 `SET NX` 로 임계치를 처음 넘은 요청 하나만 차단을 실행하게 했다.
`NX` 는 키가 없을 때만 성공하므로, 동시에 몇 개가 들어와도 정확히 하나만 통과한다.

애플리케이션 쪽은 두 값을 구분해 쓴다.

```java
if (result.count() < THRESHOLD) {
	return;                                  // 아직 임계치 미만
}

if (result.shouldBlock()) {                  // 당번만 실행
	user.block();
	userRepository.save(user);
	fraudDetectionRepository.save(FraudDetection.bruteForce(...));
}

throw new CustomException(ErrorCode.ACCOUNT_BLOCKED);   // 넘긴 요청은 전부 거부
```

`throw` 가 `if` 밖에 있는 것이 중요하다. 안으로 넣으면 나머지 19개가 차단되지 않고 통과한다.

- **거부할 것인가** → `count >= THRESHOLD`. 임계치를 넘긴 요청은 전부
- **차단을 실행할 것인가** → `shouldBlock`. 하나만

## 결과

같은 테스트가 전환 전에는 깨지고 전환 후에는 통과한다.

```
전환 전: Expected size: 1 but was: 20
전환 후: 통과
```

Sorted Set 의 member 로는 `login_attempts.id` 를 쓴다. member 는 같은 값이면 덮어쓰므로
시각을 쓰면 같은 밀리초에 들어온 두 건이 하나로 합쳐져 카운트가 누락된다.
DB 가 발급한 id 는 겹치지 않는다.

## 남긴 것

**검증이 통과하는 것만으로는 부족하다.** 전환 후 테스트가 통과하는 것은 원래부터 통과했을 가능성과
구분되지 않는다. 그래서 문제를 먼저 재현해 실패를 확인한 뒤 전환했고, 실패 출력을 기록으로 남겼다.

구현 후에 추가해 곧바로 통과한 테스트는 대상을 일부러 망가뜨려 실제로 잡아내는지 확인했다.

| 망가뜨린 것 | 깨진 테스트 |
|---|---|
| member 를 id 대신 시각으로 | `같은_시각의_두_건도_각각_센다` 외 2개 |
| `ZREMRANGEBYSCORE` 삭제 | `윈도우_밖의_기록은_세지_않는다` |
| `SET` 에서 `NX` 제거 | `임계치에_도달하면_처음_한_번만_차단_신호를_준다` |
