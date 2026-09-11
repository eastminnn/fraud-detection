package com.eastminn.fraud.detection.service;

import com.eastminn.fraud.detection.infra.FailureCount;
import com.eastminn.fraud.detection.domain.FraudDetection;
import com.eastminn.fraud.detection.repository.FraudDetectionRepository;
import com.eastminn.fraud.detection.infra.LoginAttemptCounter;
import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** 슬라이딩 윈도우 안의 로그인 실패 횟수가 임계치를 넘으면 계정을 차단한다. */
@Component
@RequiredArgsConstructor
public class BruteForceDetector {

	private static final Logger log = LoggerFactory.getLogger(BruteForceDetector.class);

	private static final Duration WINDOW = Duration.ofMinutes(5);
	private static final int THRESHOLD = 10;

	private final LoginAttemptCounter loginAttemptCounter;
	private final FraudDetectionRepository fraudDetectionRepository;
	private final UserRepository userRepository;

	/** member 로 쓸 id 가 필요하므로 시도 기록을 저장한 뒤에 호출한다. */
	public void detect(String username, String ipAddress, long attemptId) {
		FailureCount result;
		try {
			result = loginAttemptCounter.record(username, attemptId, Instant.now(), WINDOW, THRESHOLD);
		} catch (RuntimeException e) {
			// 집계할 수 없으면 판정을 건너뛴다. 이미 차단된 계정은 계정 상태로 계속 막힌다.
			log.error("브루트포스 판정을 건너뛴다. username={}", username, e);
			return;
		}

		if (result.count() < THRESHOLD) {
			return;
		}

		if (result.shouldBlock()) {
			// 윈도우가 지나면 카운트는 0 이 된다. 그 뒤에도 막으려면 계정 상태를 바꿔야 한다.
			userRepository.findByUsername(username).ifPresent(user -> {
				user.block();
				userRepository.save(user);
			});
			fraudDetectionRepository.save(FraudDetection.bruteForce(username, ipAddress, (int) result.count()));
		}

		throw new CustomException(ErrorCode.ACCOUNT_BLOCKED);
	}
}
