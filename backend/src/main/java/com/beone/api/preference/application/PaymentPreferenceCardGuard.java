package com.beone.api.preference.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.card.application.RepresentativeCardGuard;
import com.beone.api.card.application.RepresentativeCardInUseException;
import com.beone.api.preference.persistence.PaymentPreferenceEntity;
import com.beone.api.preference.persistence.PaymentPreferenceRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 카드 변경에서 결제 전 설정의 대표 카드를 지킨다(card의 {@link RepresentativeCardGuard} 구현, docs/api/cards.md, D-23).
 *
 * <ul>
 * <li>대표 카드 고정 모드의 대표 카드를 추천에서 빼거나 해제하면 {@link RepresentativeCardInUseException}(409
 * {@code REPRESENTATIVE_CARD_IN_USE})을 던진다.</li>
 * <li>자동 추천 모드의 대표 카드를 해제하면 대표 카드를 비운다.</li>
 * <li>자동 추천 모드의 대표 카드를 추천에서 빼는 것은 막지도 비우지도 않는다. 다시 넣으면 그대로 대표 카드다. 추천(B2)은 추천 비교
 * 대상이 아닌 대표 카드를 동률 처리에서 쓰지 않는다(D-15, 결정 28 관리자 판단).</li>
 * </ul>
 * 보유 카드 변경 트랜잭션 안에서 사용자 행과 설정 행을 쓰기 잠금으로 읽어 설정 변경과 차례로 처리한다. 의존성은 설정 저장소뿐이라 보유 카드
 * 서비스와 순환하지 않는다.
 */
@Component
public class PaymentPreferenceCardGuard implements RepresentativeCardGuard {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final PaymentPreferenceRepository paymentPreferenceRepository;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param paymentPreferenceRepository 설정 저장소
	 * @param clock 대표 카드를 비운 시각을 정할 시계
	 */
	@Autowired
	public PaymentPreferenceCardGuard(PaymentPreferenceRepository paymentPreferenceRepository,
			ObjectProvider<Clock> clock) {
		this(paymentPreferenceRepository, clock.getIfUnique(Clock::systemUTC));
	}

	PaymentPreferenceCardGuard(PaymentPreferenceRepository paymentPreferenceRepository, Clock clock) {
		this.paymentPreferenceRepository = Objects.requireNonNull(paymentPreferenceRepository,
				"paymentPreferenceRepository");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	@Override
	@Transactional
	public void beforeExclusion(CurrentUser user, long userCardId) {
		lockedPreference(user).filter(preference -> preference.represents(userCardId))
			.filter(PaymentPreferenceCardGuard::isFixed)
			.ifPresent(preference -> {
				throw new RepresentativeCardInUseException();
			});
	}

	@Override
	@Transactional
	public void beforeRelease(CurrentUser user, long userCardId) {
		lockedPreference(user).filter(preference -> preference.represents(userCardId))
			.ifPresent(preference -> {
				if (isFixed(preference)) {
					throw new RepresentativeCardInUseException();
				}
				preference.clearRepresentative(LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS));
				this.paymentPreferenceRepository.saveAndFlush(preference);
			});
	}

	// 보유 카드 서비스가 이미 카드 행을 잠근 뒤 불린다. 설정 변경과 같은 순서(카드 행 → 사용자 행 → 설정 행)를 지킨다.
	private Optional<PaymentPreferenceEntity> lockedPreference(CurrentUser user) {
		this.paymentPreferenceRepository.lockUser(user.userId());
		return this.paymentPreferenceRepository.findForUpdateByUserId(user.userId());
	}

	private static boolean isFixed(PaymentPreferenceEntity preference) {
		return preference.getSelectionMode() == PaymentSelectionMode.FIXED_REPRESENTATIVE;
	}

}
