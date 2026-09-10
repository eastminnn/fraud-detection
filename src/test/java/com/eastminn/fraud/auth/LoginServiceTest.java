package com.eastminn.fraud.auth;

import com.eastminn.fraud.TestcontainersConfiguration;
import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.loginattempt.LoginAttempt;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.User;
import com.eastminn.fraud.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginServiceTest {

	private static final String IP = "192.168.0.1";
	private static final String RAW_PASSWORD = "password123";

	@Autowired
	private LoginService loginService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void 초기화() {
		loginAttemptRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(User.create("minsu", passwordEncoder.encode(RAW_PASSWORD)));
	}

	@AfterEach
	void 정리() {
		loginAttemptRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 올바른_비밀번호면_로그인에_성공한다() {
		LoginResponse response = loginService.login(new LoginRequest("minsu", RAW_PASSWORD), IP);

		assertThat(response.username()).isEqualTo("minsu");
	}

	@Test
	void 비밀번호가_틀리면_로그인에_실패한다() {
		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.LOGIN_FAILED);
	}

	@Test
	void 존재하지_않는_계정도_같은_예외를_던진다() {
		assertThatThrownBy(() -> loginService.login(new LoginRequest("admin", "anything"), IP))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.LOGIN_FAILED);
	}

	@Test
	void 로그인에_실패해도_시도_기록이_남는다() {
		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class);

		List<LoginAttempt> attempts = loginAttemptRepository.findAll();
		assertThat(attempts).hasSize(1);
		assertThat(attempts.get(0).isSuccess()).isFalse();
		assertThat(attempts.get(0).getUsername()).isEqualTo("minsu");
	}

	@Test
	void 성공한_로그인도_기록된다() {
		loginService.login(new LoginRequest("minsu", RAW_PASSWORD), IP);

		List<LoginAttempt> attempts = loginAttemptRepository.findAll();
		assertThat(attempts).hasSize(1);
		assertThat(attempts.get(0).isSuccess()).isTrue();
	}
}
