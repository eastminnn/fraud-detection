package com.eastminn.fraud.auth;

import com.eastminn.fraud.TestcontainersConfiguration;
import com.eastminn.fraud.loginattempt.LoginAttemptRepository;
import com.eastminn.fraud.user.User;
import com.eastminn.fraud.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void 초기화() {
		loginAttemptRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(User.create("minsu", passwordEncoder.encode("password123")));
	}

	@AfterEach
	void 정리() {
		loginAttemptRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 비밀번호가_틀리면_401과_LOGIN_FAILED_를_반환한다() throws Exception {
		mockMvc.perform(post("/api/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "minsu", "password": "wrong"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	void 아이디가_비어있으면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/api/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username": "", "password": "password123"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
	}
}
