package com.beone.api.order.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import com.beone.api.benefit.card.MerchantClass;
import com.beone.api.benefit.rule.loading.MerchantClassAllowlist;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 주문·예정 소비가 받을 수 있는 {@code merchantClass} 허용 목록(D-23, docs/api/orders.md).
 *
 * <p>목록 원본은 {@code rules/merchant-classes.txt}이고, 규칙 로더와 같은 공개 파서
 * {@link MerchantClassAllowlist#parse(String)}를 읽기 전용으로 써서 해석 규칙이 갈라지지 않게 한다(결정 27).
 * 파일을 읽지 못하거나 목록이 잘못되면 서버 시작이 실패한다.
 */
@Component
public class MerchantClassCatalog {

	private static final String ALLOWLIST = "rules/merchant-classes.txt";

	private final Set<String> codes;

	public MerchantClassCatalog() {
		this.codes = MerchantClassAllowlist.parse(read())
			.stream()
			.map(MerchantClass::code)
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * 허용 목록에 정확히 같은 코드가 있으면 {@code true}. 대소문자·공백을 고치지 않는다.
	 * @param code 확인할 업종 코드
	 * @return 허용 목록에 있으면 {@code true}
	 */
	public boolean isAllowed(String code) {
		return code != null && this.codes.contains(code);
	}

	private static String read() {
		try (InputStream input = new ClassPathResource(ALLOWLIST).getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("merchant class allowlist could not be read", exception);
		}
	}

}
