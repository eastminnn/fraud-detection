package com.eastminn.fraud.loginattempt;

import com.eastminn.fraud.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginAttemptRepositoryTest {

	private static final String IP = "192.168.0.1";

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@Test
	void 존재하지_않는_계정의_시도도_기록된다() {
		LoginAttempt saved = loginAttemptRepository.save(LoginAttempt.failure("admin", IP));

		assertThat(saved.getUsername()).isEqualTo("admin");
	}

	@Test
	void 윈도우_안의_실패_횟수만_센다() {
		Instant now = Instant.now();
		saveFailureAt("minsu", now.minus(Duration.ofMinutes(1)));
		saveFailureAt("minsu", now.minus(Duration.ofMinutes(2)));
		saveFailureAt("minsu", now.minus(Duration.ofMinutes(10)));

		long count = loginAttemptRepository.countFailuresSince("minsu", now.minus(Duration.ofMinutes(5)));

		assertThat(count).isEqualTo(2);
	}

	@Test
	void 성공한_시도는_실패_횟수에_포함되지_않는다() {
		Instant now = Instant.now();
		saveFailureAt("minsu", now.minus(Duration.ofMinutes(1)));
		loginAttemptRepository.save(LoginAttempt.success("minsu", IP));

		long count = loginAttemptRepository.countFailuresSince("minsu", now.minus(Duration.ofMinutes(5)));

		assertThat(count).isEqualTo(1);
	}

	@Test
	void 다른_계정의_실패는_세지_않는다() {
		Instant now = Instant.now();
		saveFailureAt("minsu", now.minus(Duration.ofMinutes(1)));
		saveFailureAt("jieun", now.minus(Duration.ofMinutes(1)));

		long count = loginAttemptRepository.countFailuresSince("minsu", now.minus(Duration.ofMinutes(5)));

		assertThat(count).isEqualTo(1);
	}

	private void saveFailureAt(String username, Instant attemptedAt) {
		loginAttemptRepository.save(LoginAttempt.of(username, IP, false, attemptedAt));
	}
}
