package com.eastminn.fraud.loginattempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String username;

	@Column(name = "ip_address", nullable = false, length = 45)
	private String ipAddress;

	@Column(nullable = false)
	private boolean success;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	private LoginAttempt(String username, String ipAddress, boolean success, Instant attemptedAt) {
		this.username = username;
		this.ipAddress = ipAddress;
		this.success = success;
		this.attemptedAt = attemptedAt;
	}

	public static LoginAttempt of(String username, String ipAddress, boolean success, Instant attemptedAt) {
		return new LoginAttempt(username, ipAddress, success, attemptedAt);
	}

	public static LoginAttempt success(String username, String ipAddress) {
		return new LoginAttempt(username, ipAddress, true, Instant.now());
	}

	public static LoginAttempt failure(String username, String ipAddress) {
		return new LoginAttempt(username, ipAddress, false, Instant.now());
	}
}
