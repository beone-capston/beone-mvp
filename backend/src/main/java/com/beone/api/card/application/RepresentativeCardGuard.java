package com.beone.api.card.application;

import com.beone.api.user.application.CurrentUser;

/**
 * 결제 전 설정의 대표 카드와 보유 카드 변경을 맞추는 확장 지점(D-23, docs/api/cards.md).
 *
 * <p>결제 전 설정(A4)에서 구현한다. 구현 빈이 없으면 {@link #NONE}을 써서 아무것도 막지 않는다. A4 구현은 다음을 맡는다.
 * <ul>
 * <li>대표 카드 고정 모드의 대표 카드를 추천에서 빼거나 해제하면 409 {@code REPRESENTATIVE_CARD_IN_USE}가 되도록 예외를 던진다.</li>
 * <li>자동 추천 모드에서 동률 기준으로만 쓰는 대표 카드를 해제하면 대표 카드를 비운다.</li>
 * </ul>
 * 두 메서드는 보유 카드 변경과 같은 트랜잭션 안에서 변경 전에 호출된다.
 */
public interface RepresentativeCardGuard {

	/** 대표 카드 설정이 없을 때 쓰는 기본 구현. 아무것도 막지 않는다. */
	RepresentativeCardGuard NONE = new RepresentativeCardGuard() {
	};

	/**
	 * 카드를 추천에서 빼기 전에 호출한다.
	 * @param user 현재 사용자
	 * @param userCardId 저장소 카드 번호
	 */
	default void beforeExclusion(CurrentUser user, long userCardId) {
	}

	/**
	 * 카드 등록을 해제하기 전에 호출한다.
	 * @param user 현재 사용자
	 * @param userCardId 저장소 카드 번호
	 */
	default void beforeRelease(CurrentUser user, long userCardId) {
	}

}
