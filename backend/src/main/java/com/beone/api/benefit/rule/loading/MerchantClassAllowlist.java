package com.beone.api.benefit.rule.loading;

import java.util.LinkedHashSet;
import java.util.Set;

import com.beone.api.benefit.card.MerchantClass;

/**
 * 허용 업종 코드 목록 파일을 읽는다. 한 줄에 코드 하나이고 {@code #}으로 시작하는 줄과 빈 줄은 건너뛴다.
 * 오타가 "대상 불일치"로 조용히 처리되지 않게 규칙 데이터의 코드를 이 목록과 대조한다(D-20).
 */
public final class MerchantClassAllowlist {

	private MerchantClassAllowlist() {
	}

	public static Set<MerchantClass> parse(String content) {
		Set<MerchantClass> classes = new LinkedHashSet<>();
		for (String line : content.split("\\R")) {
			String code = line.strip();
			if (code.isEmpty() || code.startsWith("#")) {
				continue;
			}
			if (!classes.add(MerchantClass.of(code))) {
				throw new IllegalArgumentException("merchant class allowlist has a duplicate: " + code);
			}
		}
		if (classes.isEmpty()) {
			throw new IllegalArgumentException("merchant class allowlist is empty");
		}
		return Set.copyOf(classes);
	}

}
