package com.eastminn.fraud.common.exception;

import com.eastminn.fraud.common.exception.error.ErrorCode;
import com.eastminn.fraud.common.exception.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
		ErrorCode errorCode = e.getErrorCode();

		// 로그인 실패는 정상 흐름에서 자주 발생한다. error 로 남기면
		// 브루트포스 공격 시 로그가 폭증해 실제 장애를 놓친다.
		if (errorCode.getStatus().is5xxServerError()) {
			log.error("[{}] {}", errorCode.name(), errorCode.getMessage(), e);
		} else {
			log.warn("[{}] {}", errorCode.name(), errorCode.getMessage());
		}

		return ResponseEntity.status(errorCode.getStatus())
				.body(ErrorResponse.of(errorCode));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		log.warn("[{}] {}", ErrorCode.INVALID_INPUT_VALUE.name(), e.getBindingResult().getFieldErrors());

		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
				.body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE));
	}

	// 예상하지 못한 예외. 원본 메시지를 응답에 노출하지 않는다.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
		log.error("처리되지 않은 예외", e);

		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
				.body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
	}
}
