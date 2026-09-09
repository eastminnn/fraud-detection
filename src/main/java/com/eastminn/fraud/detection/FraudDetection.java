package com.eastminn.fraud.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "fraud_detections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FraudDetection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String username;

	@Column(name = "ip_address", nullable = false, length = 45)
	private String ipAddress;

	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 20)
	private RuleType ruleType;

	/** 탐지 시점에 윈도우 안에서 집계된 건수. 판정 근거를 남기기 위해 함께 기록한다. */
	@Column(name = "trigger_count", nullable = false)
	private int triggerCount;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_taken", nullable = false, length = 20)
	private ActionTaken actionTaken;

	private FraudDetection(String username, String ipAddress, RuleType ruleType,
	                       int triggerCount, ActionTaken actionTaken) {
		this.username = username;
		this.ipAddress = ipAddress;
		this.ruleType = ruleType;
		this.triggerCount = triggerCount;
		this.actionTaken = actionTaken;
		this.detectedAt = Instant.now();
	}

	public static FraudDetection bruteForce(String username, String ipAddress, int triggerCount) {
		return new FraudDetection(username, ipAddress, RuleType.BRUTE_FORCE, triggerCount, ActionTaken.BLOCKED);
	}
}
