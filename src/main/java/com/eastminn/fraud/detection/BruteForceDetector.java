package com.eastminn.fraud.detection;

import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** 슬라이딩 윈도우 안의 로그인 실패 횟수가 임계치를 넘으면 계정을 차단한다. */
@Component
@RequiredArgsConstructor
public class BruteForceDetector {

	private static final Duration WINDOW = Duration.ofMinutes(5);
	private static final int THRESHOLD = 10;

	private final LoginAttemptRepository loginAttemptRepository;
	private final FraudDetectionRepository fraudDetectionRepository;
	private final UserRepository userRepository;

	/** 방금 실패한 시도까지 세야 하므로 시도 기록을 저장한 뒤에 호출한다. */
	public void detect(String username, String ipAddress) {
		long failures = loginAttemptRepository.countFailuresSince(username, Instant.now().minus(WINDOW));

		if (failures < THRESHOLD) {
			return;
		}

		// 윈도우가 지나면 카운트는 0 이 된다. 그 뒤에도 막으려면 계정 상태를 바꿔야 한다.
		userRepository.findByUsername(username).ifPresent(user -> {
			user.block();
			userRepository.save(user);
		});

		fraudDetectionRepository.save(FraudDetection.bruteForce(username, ipAddress, (int) failures));

		throw new CustomException(ErrorCode.ACCOUNT_BLOCKED);
	}
}
