package com.eastminn.fraud.detection;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 윈도우 안의 로그인 실패 횟수를 Redis Sorted Set 으로 집계한다. */
@Component
@RequiredArgsConstructor
public class LoginAttemptCounter {

	private static final Duration KEY_TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate redisTemplate;

	@SuppressWarnings("rawtypes")
	private final RedisScript<List> recordLoginFailureScript;

	/** member 로 쓸 id 가 필요하므로 시도 기록을 저장한 뒤에 호출한다. */
	@SuppressWarnings("unchecked")
	public FailureCount record(String username, long attemptId, Instant now, Duration window, int threshold) {
		List<Long> result = redisTemplate.execute(
				recordLoginFailureScript,
				List.of(failKey(username), blockedKey(username)),
				String.valueOf(now.toEpochMilli()),
				String.valueOf(now.minus(window).toEpochMilli()),
				String.valueOf(attemptId),
				String.valueOf(KEY_TTL.toSeconds()),
				String.valueOf(threshold));

		return new FailureCount(result.get(0), result.get(1) == 1L);
	}

	private String failKey(String username) {
		return "login:fail:" + username;
	}

	private String blockedKey(String username) {
		return "login:blocked:" + username;
	}
}
