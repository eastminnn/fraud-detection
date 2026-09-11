package com.eastminn.fraud.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

		@NotBlank(message = "아이디를 입력해주세요.")
		@Size(max = 50, message = "아이디는 50자를 넘을 수 없습니다.")
		String username,

		@NotBlank(message = "비밀번호를 입력해주세요.")
		String password
) {
}
