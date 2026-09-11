package com.eastminn.fraud.auth;

import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.detection.BruteForceDetector;
import com.eastminn.fraud.loginattempt.LoginAttempt;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.User;
import com.eastminn.fraud.user.UserRepository;
import com.eastminn.fraud.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

	private final UserRepository userRepository;
	private final LoginAttemptRepository loginAttemptRepository;
	private final PasswordEncoder passwordEncoder;
	private final BruteForceDetector bruteForceDetector;

	public LoginResponse login(LoginRequest request, String ipAddress) {
		User user = userRepository.findByUsername(request.username()).orElse(null);

		if (user != null && user.getStatus() == UserStatus.BLOCKED) {
			throw new CustomException(ErrorCode.ACCOUNT_BLOCKED);
		}

		boolean success = user != null && passwordEncoder.matches(request.password(), user.getPasswordHash());

		LoginAttempt attempt = loginAttemptRepository.save(
				success
						? LoginAttempt.success(request.username(), ipAddress)
						: LoginAttempt.failure(request.username(), ipAddress));

		if (!success) {
			bruteForceDetector.detect(request.username(), ipAddress, attempt.getId());
			throw new CustomException(ErrorCode.LOGIN_FAILED);
		}

		return new LoginResponse(user.getUsername());
	}
}
