package com.beone.api.codef.application;

import java.util.Objects;

import com.beone.api.card.application.CodefConnectionLookup;
import com.beone.api.codef.persistence.CodefConnectionRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 보유 카드의 {@link CodefConnectionLookup} 구현(#136). 연결 행({@code codef_connections})이 있으면 연결된 것으로 본다.
 *
 * <p>연결 상태 GET과 같은 기준이다. 연결 행은 {@code CONNECTED} 또는 {@code RECONNECT_REQUIRED}이고 둘 다 연결이 있는 상태다
 * ({@code status}가 {@code NOT_CONNECTED}가 아님). 재연결 필요 여부는 연결 상태 GET의 {@code reconnectRequired}로 알린다. 가상
 * 포트폴리오는 연결할 수 없어 행이 없으므로 항상 {@code false}다.
 */
@Component
class CodefConnectionStatusLookup implements CodefConnectionLookup {

	private final CodefConnectionRepository codefConnectionRepository;

	CodefConnectionStatusLookup(CodefConnectionRepository codefConnectionRepository) {
		this.codefConnectionRepository = Objects.requireNonNull(codefConnectionRepository, "codefConnectionRepository");
	}

	@Override
	public boolean isConnected(CurrentUser user) {
		return this.codefConnectionRepository.existsById(user.userId());
	}

}
