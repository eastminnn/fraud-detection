package com.eastminn.fraud.detection.repository;

import com.eastminn.fraud.detection.domain.FraudDetection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDetectionRepository extends JpaRepository<FraudDetection, Long> {
}
