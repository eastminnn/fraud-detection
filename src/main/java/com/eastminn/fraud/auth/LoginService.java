package com.eastminn.fraud.auth;

import com.eastminn.fraud.common.exception.CustomException;
import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.loginattempt.LoginAttempt;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.User;
import com.eastminn.fraud.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

	private final UserRepository userRepository;
	private final LoginAttemptRepository loginAttemptRepository;
	private final PasswordEncoder passwordEncoder;

	public LoginResponse login(LoginRequest request, String ipAddress) {
		User user = userRepository.findByUsername(request.username()).orElse(null);
		boolean success = user != null && passwordEncoder.matches(request.password(), user.getPasswordHash());

		loginAttemptRepository.save(
				success
						? LoginAttempt.success(request.username(), ipAddress)
						: LoginAttempt.failure(request.username(), ipAddress));

		if (!success) {
			throw new CustomException(ErrorCode.LOGIN_FAILED);
		}

		return new LoginResponse(user.getUsername());
	}
}
