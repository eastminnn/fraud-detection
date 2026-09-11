package com.eastminn.fraud.loginattempt.repository;

import com.eastminn.fraud.loginattempt.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
}
