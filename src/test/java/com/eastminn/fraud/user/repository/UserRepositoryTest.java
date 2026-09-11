package com.eastminn.fraud.user.repository;

import com.eastminn.fraud.user.domain.User;
import com.eastminn.fraud.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void 같은_아이디로_두_번_가입할_수_없다() {
		userRepository.save(User.create("minsu", "hashed-password"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(User.create("minsu", "other-password")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
