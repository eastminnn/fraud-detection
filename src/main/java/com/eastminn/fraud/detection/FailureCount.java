package com.eastminn.fraud.detection;

/**
 * @param count       윈도우 안의 실패 횟수
 * @param shouldBlock 이 요청이 차단을 실행해야 하는지. 동시에 임계치를 넘겨도 하나만 true 다.
 */
public record FailureCount(long count, boolean shouldBlock) {
}
