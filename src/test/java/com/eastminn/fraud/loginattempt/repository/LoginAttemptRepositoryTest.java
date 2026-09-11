package com.eastminn.fraud.loginattempt.repository;

import com.eastminn.fraud.loginattempt.domain.LoginAttempt;
import com.eastminn.fraud.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

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
}
