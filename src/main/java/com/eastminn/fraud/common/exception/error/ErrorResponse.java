package com.eastminn.fraud.common.exception.error;

public record ErrorResponse(int status, String code, String message) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(
				errorCode.getStatus().value(),
				errorCode.name(),
				errorCode.getMessage()
		);
	}
}
