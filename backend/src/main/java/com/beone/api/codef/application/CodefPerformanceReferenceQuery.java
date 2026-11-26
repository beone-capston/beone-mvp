package com.beone.api.codef.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.beone.api.codef.persistence.CodefPerformanceReferenceEntity;
import com.beone.api.codef.persistence.CodefPerformanceReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 카드사 조회 실적(참고)을 다른 패키지가 읽는 창구(D-16, 이슈 #281). 월간 상태가 전월 실적 참고값을 보여 주고, 사용자가 참고해 입력한
 * 값을 기록하는 데 쓴다. 읽기만 하며 참고값을 계산 입력으로 바꾸지 않는다.
 *
 * <p>보유 카드에 연결된 참고값만 돌려준다. 카드 미연결 참고값({@code user_card_id} 없음)은 어느 카드의 값인지 모르므로 나오지 않는다. 금액을 해석하지
 * 못한 참고값은 {@link Reference#currentUseWon()}이 {@code null}이며 0으로 바꾸지 않는다.
 */
@Service
public class CodefPerformanceReferenceQuery {

	private final CodefPerformanceReferenceRepository repository;

	public CodefPerformanceReferenceQuery(CodefPerformanceReferenceRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	/**
	 * 이 카드에 연결된 참고값 중 반영기간이 정확히 {@code from}~{@code to}인 최신 1건.
	 * @param userId 사용자 ID
	 * @param userCardId 보유 카드 ID
	 * @param from 반영기간 시작일
	 * @param to 반영기간 종료일
	 * @return 최신 참고값, 없으면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<Reference> latest(long userId, long userCardId, LocalDate from, LocalDate to) {
		return this.repository
			.findFirstByUserIdAndUserCardIdAndPeriodFromAndPeriodToOrderByFetchedAtDescCodefPerformanceReferenceIdDesc(
					userId, userCardId, from, to)
			.flatMap(CodefPerformanceReferenceQuery::toReference);
	}

	/**
	 * 이 사용자의 참고값 하나. 카드에 연결되지 않았거나 반영기간이 없는 참고값은 빈 값이다.
	 * @param userId 사용자 ID
	 * @param referenceId 참고값 ID
	 * @return 참고값, 없거나 다른 사용자의 것이면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<Reference> find(long userId, long referenceId) {
		return this.repository.findByCodefPerformanceReferenceIdAndUserId(referenceId, userId)
			.flatMap(CodefPerformanceReferenceQuery::toReference);
	}

	private static Optional<Reference> toReference(CodefPerformanceReferenceEntity entity) {
		if (entity.getUserCardId() == null || entity.getPeriodFrom() == null || entity.getPeriodTo() == null) {
			return Optional.empty();
		}
		return Optional.of(new Reference(entity.getCodefPerformanceReferenceId(), entity.getSyncId(),
				entity.getUserCardId(), entity.getPeriodFrom(), entity.getPeriodTo(), entity.getCurrentUseWon(),
				entity.getFetchedAt()));
	}

	/**
	 * 카드사 조회 실적(참고) 하나. 계산에 쓰지 않는다.
	 *
	 * @param referenceId 참고값 ID
	 * @param syncId 이 값을 저장한 동기화 ID
	 * @param userCardId 연결된 보유 카드 ID
	 * @param periodFrom 반영기간 시작일(조회월 1일)
	 * @param periodTo 반영기간 종료일
	 * @param currentUseWon 현재이용금액. 해석하지 못했으면 {@code null}
	 * @param fetchedAt 조회 시각(서울)
	 */
	public record Reference(long referenceId, long syncId, long userCardId, LocalDate periodFrom, LocalDate periodTo,
			Long currentUseWon, LocalDateTime fetchedAt) {

		public Reference {
			Objects.requireNonNull(periodFrom, "periodFrom");
			Objects.requireNonNull(periodTo, "periodTo");
			Objects.requireNonNull(fetchedAt, "fetchedAt");
		}

	}

	/**
	 * 참고값 ID의 외부 표현. {@code cpr-} 뒤에 저장소 번호를 선행 0 없이 붙인다(예: {@code cpr-12}).
	 */
	public static final class ReferenceId {

		private static final String PREFIX = "cpr-";

		private static final Pattern FORMAT = Pattern.compile("cpr-[1-9][0-9]{0,18}");

		private ReferenceId() {
		}

		/**
		 * 저장소 번호를 외부 표현으로 바꾼다.
		 * @param id 양수 저장소 번호
		 * @return {@code cpr-} 형식의 외부 표현
		 */
		public static String format(long id) {
			if (id < 1) {
				throw new IllegalArgumentException("reference id must be positive");
			}
			return PREFIX + id;
		}

		/**
		 * 외부 표현을 저장소 번호로 바꾼다.
		 * @param value 받은 값
		 * @return 형식이 맞으면 번호, 아니면 빈 값
		 */
		public static Optional<Long> parse(String value) {
			if (value == null || !FORMAT.matcher(value).matches()) {
				return Optional.empty();
			}
			try {
				return Optional.of(Long.parseLong(value.substring(PREFIX.length())));
			}
			catch (NumberFormatException overflow) {
				return Optional.empty();
			}
		}

	}

}
