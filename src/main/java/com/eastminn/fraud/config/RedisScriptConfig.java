package com.eastminn.fraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class RedisScriptConfig {

	@Bean
	@SuppressWarnings("rawtypes")
	public RedisScript<List> recordLoginFailureScript() {
		DefaultRedisScript<List> script = new DefaultRedisScript<>();
		script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/record-login-failure.lua")));
		script.setResultType(List.class);
		return script;
	}
}
