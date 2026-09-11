package com.eastminn.fraud.auth;

import com.eastminn.fraud.TestcontainersConfiguration;
import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.detection.FraudDetection;
import com.eastminn.fraud.detection.FraudDetectionRepository;
import com.eastminn.fraud.detection.RuleType;
import com.eastminn.fraud.loginattempt.LoginAttempt;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.User;
import com.eastminn.fraud.user.UserStatus;
import com.eastminn.fraud.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
	private FraudDetectionRepository fraudDetectionRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void 초기화() {
		fraudDetectionRepository.deleteAll();
		loginAttemptRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(User.create("minsu", passwordEncoder.encode(RAW_PASSWORD)));
	}

	@AfterEach
	void 정리() {
		fraudDetectionRepository.deleteAll();
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

	@Test
	void 실패가_9번이면_차단하지_않는다() {
		실패기록을_남긴다(8);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.LOGIN_FAILED);
	}

	@Test
	void 실패가_10번이면_차단한다() {
		실패기록을_남긴다(9);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.ACCOUNT_BLOCKED);
	}

	@Test
	void 차단되면_판정_근거가_기록된다() {
		실패기록을_남긴다(9);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class);

		List<FraudDetection> detections = fraudDetectionRepository.findAll();
		assertThat(detections).hasSize(1);
		assertThat(detections.get(0).getUsername()).isEqualTo("minsu");
		assertThat(detections.get(0).getIpAddress()).isEqualTo(IP);
		assertThat(detections.get(0).getRuleType()).isEqualTo(RuleType.BRUTE_FORCE);
		assertThat(detections.get(0).getTriggerCount()).isEqualTo(10);
	}

	@Test
	void 차단되면_계정_상태가_BLOCKED_로_바뀐다() {
		실패기록을_남긴다(9);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class);

		User user = userRepository.findByUsername("minsu").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
	}

	@Test
	void 차단된_계정은_올바른_비밀번호로도_로그인할_수_없다() {
		실패기록을_남긴다(9);
		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", "wrong"), IP))
				.isInstanceOf(CustomException.class);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("minsu", RAW_PASSWORD), IP))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.ACCOUNT_BLOCKED);
	}

	@Test
	@Disabled("DB 집계로는 통과할 수 없다. Redis 전환 후 활성화한다")
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
					// 로그인 실패 예외는 무시한다. 검증 대상은 탐지 기록 수다.
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

	private void 실패기록을_남긴다(int count) {
		Instant now = Instant.now();
		for (int i = 0; i < count; i++) {
			loginAttemptRepository.save(LoginAttempt.of("minsu", IP, false, now.minusSeconds(i)));
		}
	}
}
