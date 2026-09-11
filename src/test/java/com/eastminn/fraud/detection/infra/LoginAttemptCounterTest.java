package com.eastminn.fraud.detection.infra;

import com.eastminn.fraud.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginAttemptCounterTest {

	private static final Duration WINDOW = Duration.ofMinutes(5);
	private static final int THRESHOLD = 10;
	private static final String USERNAME = "minsu";

	@Autowired
	private LoginAttemptCounter counter;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void 초기화() {
		redisTemplate.delete("login:fail:" + USERNAME);
		redisTemplate.delete("login:blocked:" + USERNAME);
	}

	@Test
	void 실패를_기록하면_개수가_늘어난다() {
		Instant now = Instant.now();

		FailureCount first = counter.record(USERNAME, 1L, now, WINDOW, THRESHOLD);
		FailureCount second = counter.record(USERNAME, 2L, now, WINDOW, THRESHOLD);

		assertThat(first.count()).isEqualTo(1);
		assertThat(second.count()).isEqualTo(2);
	}

	@Test
	void 윈도우_밖의_기록은_세지_않는다() {
		Instant now = Instant.now();

		counter.record(USERNAME, 1L, now.minus(Duration.ofMinutes(10)), WINDOW, THRESHOLD);
		FailureCount result = counter.record(USERNAME, 2L, now, WINDOW, THRESHOLD);

		assertThat(result.count()).isEqualTo(1);
	}

	@Test
	void 같은_시각의_두_건도_각각_센다() {
		Instant now = Instant.now();

		counter.record(USERNAME, 1L, now, WINDOW, THRESHOLD);
		FailureCount result = counter.record(USERNAME, 2L, now, WINDOW, THRESHOLD);

		assertThat(result.count()).isEqualTo(2);
	}

	@Test
	void 임계치에_도달하면_처음_한_번만_차단_신호를_준다() {
		Instant now = Instant.now();

		for (int i = 1; i < THRESHOLD; i++) {
			assertThat(counter.record(USERNAME, i, now, WINDOW, THRESHOLD).shouldBlock()).isFalse();
		}

		FailureCount 임계치도달 = counter.record(USERNAME, THRESHOLD, now, WINDOW, THRESHOLD);
		FailureCount 그다음 = counter.record(USERNAME, THRESHOLD + 1, now, WINDOW, THRESHOLD);

		assertThat(임계치도달.count()).isEqualTo(THRESHOLD);
		assertThat(임계치도달.shouldBlock()).isTrue();
		assertThat(그다음.shouldBlock()).isFalse();
	}
}
