package com.beone.api.common.web;

/**
 * 성공 응답 본문. 실제 결과는 {@code data}에만 담고 공통 성공 코드·메시지는 두지 않는다.
 *
 * <p>컨트롤러가 직접 반환한다. 204·다운로드·스트리밍·Actuator 응답은 감싸지 않는다.
 */
public record ApiResponse<T>(T data) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(data);
	}

}
