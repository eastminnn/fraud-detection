package com.eastminn.fraud.detection;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDetectionRepository extends JpaRepository<FraudDetection, Long> {
}
