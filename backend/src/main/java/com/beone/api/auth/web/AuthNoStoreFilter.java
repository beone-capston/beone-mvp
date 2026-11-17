package com.beone.api.auth.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /api/v1/auth/**}의 모든 응답(실패·404·405 포함)에 {@code Cache-Control: no-store}를 붙인다(docs/api/auth.md).
 *
 * <p>핸들러 조회 전에 실행되는 서블릿 필터라 MVC 인터셉터가 돌지 않는 404·405 응답에도 적용된다. 헤더만 쓰고 요청 값은 읽지 않는다.
 */
class AuthNoStoreFilter extends OncePerRequestFilter {

	private static final String NO_STORE = CacheControl.noStore().getHeaderValue();

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
		filterChain.doFilter(request, response);
	}

}
