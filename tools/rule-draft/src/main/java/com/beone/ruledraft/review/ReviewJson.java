package com.beone.ruledraft.review;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * compare·approve가 입력을 읽고 결과를 쓸 때 함께 쓰는 ObjectMapper를 만든다.
 *
 * <p>소수는 double을 거치지 않고 BigDecimal로 읽는다. double로 읽으면 0.10000000000000001이 0.1로 뭉개져
 * 다른 값이 일치로 보인다. 10000과 10000.0은 {@link RuleComparator#sameValue}가 값으로 비교해 일치로 본다.
 */
public final class ReviewJson {

	private ReviewJson() {
	}

	public static ObjectMapper mapper() {
		return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
				.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
	}

}
