package com.eastminn.fraud;

import org.springframework.boot.SpringApplication;

public class TestFraudApplication {

	public static void main(String[] args) {
		SpringApplication.from(FraudApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
