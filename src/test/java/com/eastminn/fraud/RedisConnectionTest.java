package com.eastminn.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisConnectionTest {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void 값을_쓰고_읽는다() {
		redisTemplate.opsForValue().set("test:key", "value");

		assertThat(redisTemplate.opsForValue().get("test:key")).isEqualTo("value");
	}
}
