package com.eastminn.fraud.auth.controller;

import com.eastminn.fraud.auth.dto.LoginRequest;
import com.eastminn.fraud.auth.dto.LoginResponse;
import com.eastminn.fraud.auth.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoginController {

	private final LoginService loginService;

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return loginService.login(request, httpRequest.getRemoteAddr());
	}
}
