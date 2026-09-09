package com.eastminn.fraud.loginattempt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

	/**
	 * 슬라이딩 윈도우 안의 로그인 실패 횟수.
	 *
	 * <p>1단계에서는 DB 를 직접 집계한다. idx_login_attempts_username_time 인덱스를 사용한다.
	 * 처리량이 올라가면 이 집계가 병목이 되며, Redis 로 옮긴다.
	 */
	@Query("""
			select count(a)
			from LoginAttempt a
			where a.username = :username
			  and a.success = false
			  and a.attemptedAt >= :since
			""")
	long countFailuresSince(@Param("username") String username, @Param("since") Instant since);
}
