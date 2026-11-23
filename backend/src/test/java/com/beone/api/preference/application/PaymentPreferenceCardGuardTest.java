package com.beone.api.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.card.application.RepresentativeCardInUseException;
import com.beone.api.preference.persistence.PaymentPreferenceEntity;
import com.beone.api.preference.persistence.PaymentPreferenceRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 대표 카드 보호 규칙을 확인한다. 기대값은 docs/api/cards.md의 PATCH·DELETE 409와 해제 문장(D-23), 자동 추천 모드 추천 제외는 결정 28
 * 관리자 판단(막지도 비우지도 않음)이다.
 */
class PaymentPreferenceCardGuardTest {

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC);

	private static final LocalDateTime SAVED = LocalDateTime.of(2026, 11, 3, 10, 30, 0);

	private PaymentPreferenceRepository repository;

	private PaymentPreferenceCardGuard guard;

	@BeforeEach
	void setUp() {
		this.repository = mock(PaymentPreferenceRepository.class);
		this.guard = new PaymentPreferenceCardGuard(this.repository, CLOCK);
	}

	@Test
	void withoutPreferenceNothingIsBlocked() {
		given(this.repository.findForUpdateByUserId(1L)).willReturn(Optional.empty());

		this.guard.beforeExclusion(MY_CARDS, 1001L);
		this.guard.beforeRelease(MY_CARDS, 1001L);

		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void fixedRepresentativeCannotBeExcludedOrReleased() {
		preference(PaymentSelectionMode.FIXED_REPRESENTATIVE, 1001L);

		assertThatThrownBy(() -> this.guard.beforeExclusion(MY_CARDS, 1001L))
			.isInstanceOf(RepresentativeCardInUseException.class);
		assertThatThrownBy(() -> this.guard.beforeRelease(MY_CARDS, 1001L))
			.isInstanceOf(RepresentativeCardInUseException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void otherCardsAreNotBlockedInFixedMode() {
		PaymentPreferenceEntity entity = preference(PaymentSelectionMode.FIXED_REPRESENTATIVE, 1001L);

		this.guard.beforeExclusion(MY_CARDS, 1002L);
		this.guard.beforeRelease(MY_CARDS, 1002L);

		assertThat(entity.getRepresentativeUserCardId()).isEqualTo(1001L);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void autoModeRepresentativeExclusionKeepsRepresentative() {
		PaymentPreferenceEntity entity = preference(PaymentSelectionMode.AUTO_RECOMMEND, 1001L);

		this.guard.beforeExclusion(MY_CARDS, 1001L);

		assertThat(entity.getRepresentativeUserCardId()).isEqualTo(1001L);
		assertThat(entity.getUpdatedAt()).isEqualTo(SAVED);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void autoModeRepresentativeReleaseClearsRepresentative() {
		PaymentPreferenceEntity entity = preference(PaymentSelectionMode.AUTO_RECOMMEND, 1001L);

		this.guard.beforeRelease(MY_CARDS, 1001L);

		assertThat(entity.getSelectionMode()).isEqualTo(PaymentSelectionMode.AUTO_RECOMMEND);
		assertThat(entity.getRepresentativeUserCardId()).isNull();
		assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30, 0));
		verify(this.repository).saveAndFlush(entity);
	}

	@Test
	void locksUserRowBeforePreferenceRow() {
		preference(PaymentSelectionMode.AUTO_RECOMMEND, null);

		this.guard.beforeRelease(MY_CARDS, 1001L);

		InOrder order = inOrder(this.repository);
		order.verify(this.repository).lockUser(1L);
		order.verify(this.repository).findForUpdateByUserId(1L);
	}

	@Test
	void fixedModeEntityRefusesToClearRepresentative() {
		PaymentPreferenceEntity entity = new PaymentPreferenceEntity(1L, PaymentSelectionMode.FIXED_REPRESENTATIVE,
				1001L, SAVED);

		assertThatThrownBy(() -> entity.clearRepresentative(SAVED)).isInstanceOf(IllegalStateException.class);
		assertThat(entity.represents(1001L)).isTrue();
		assertThat(entity.represents(1002L)).isFalse();
	}

	private PaymentPreferenceEntity preference(PaymentSelectionMode mode, Long cardId) {
		PaymentPreferenceEntity entity = new PaymentPreferenceEntity(1L, mode, cardId, SAVED);
		given(this.repository.findForUpdateByUserId(1L)).willReturn(Optional.of(entity));
		return entity;
	}

}
