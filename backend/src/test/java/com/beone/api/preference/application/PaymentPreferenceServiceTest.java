package com.beone.api.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.beone.api.card.application.ConfirmationMethod;
import com.beone.api.card.application.HoldingSource;
import com.beone.api.card.application.MappingStatus;
import com.beone.api.card.application.RecommendationEligibility;
import com.beone.api.card.application.UserCardMapping;
import com.beone.api.card.application.UserCardNotFoundException;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.preference.persistence.PaymentPreferenceEntity;
import com.beone.api.preference.persistence.PaymentPreferenceRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

/**
 * 결제 전 설정 서비스의 미설정 표시·대표 카드 검사·저장 순서를 확인한다. 기대값은 docs/api/preferences.md와 결정 28 관리자 판단이다.
 */
class PaymentPreferenceServiceTest {

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	// 서울 2026-11-17 10:30:00
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T01:30:00.700Z"), ZoneOffset.UTC);

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 30, 0);

	private PaymentPreferenceRepository repository;

	private UserCardService userCardService;

	private PaymentPreferenceService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(PaymentPreferenceRepository.class);
		this.userCardService = mock(UserCardService.class);
		this.service = new PaymentPreferenceService(this.repository, this.userCardService, CLOCK);
	}

	@Test
	void getReturnsNotSetMarkerWhenNeverSaved() {
		given(this.repository.findById(1L)).willReturn(Optional.empty());

		PaymentPreferenceView view = this.service.get(MY_CARDS);

		assertThat(view).isEqualTo(new PaymentPreferenceView(PaymentSelectionMode.AUTO_RECOMMEND, null, null));
	}

	@Test
	void getReturnsSavedValuesWithExternalCardId() {
		LocalDateTime saved = LocalDateTime.of(2026, 11, 3, 10, 30, 0);
		given(this.repository.findById(1L)).willReturn(Optional
			.of(new PaymentPreferenceEntity(1L, PaymentSelectionMode.FIXED_REPRESENTATIVE, 1001L, saved)));

		assertThat(this.service.get(MY_CARDS)).isEqualTo(
				new PaymentPreferenceView(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001", saved));
	}

	@Test
	void changeLocksCardThenUserBeforeReadingCardsAndSavesEligibleCard() {
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(confirmed("uc-1001", false)));

		PaymentPreferenceView view = this.service.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001"));

		assertThat(view).isEqualTo(new PaymentPreferenceView(PaymentSelectionMode.FIXED_REPRESENTATIVE, "uc-1001", NOW));
		InOrder order = inOrder(this.repository, this.userCardService);
		order.verify(this.repository).lockUserCard(1001L);
		order.verify(this.repository).lockUser(1L);
		order.verify(this.userCardService).list(MY_CARDS);
		order.verify(this.repository).upsert(1L, "FIXED_REPRESENTATIVE", 1001L, NOW);
	}

	@Test
	void autoRecommendWithNullClearsRepresentativeWithoutReadingCards() {
		PaymentPreferenceView view = this.service.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.AUTO_RECOMMEND, null));

		assertThat(view).isEqualTo(new PaymentPreferenceView(PaymentSelectionMode.AUTO_RECOMMEND, null, NOW));
		verify(this.repository).upsert(1L, "AUTO_RECOMMEND", null, NOW);
		verify(this.repository, never()).lockUserCard(anyLong());
		verifyNoInteractions(this.userCardService);
	}

	@Test
	void autoRecommendKeepsEligibleRepresentativeForTieBreak() {
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(synthetic("uc-7", false)));

		PaymentPreferenceView view = this.service.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.AUTO_RECOMMEND, "uc-7"));

		assertThat(view.representativeUserCardId()).isEqualTo("uc-7");
		verify(this.repository).upsert(1L, "AUTO_RECOMMEND", 7L, NOW);
	}

	@ParameterizedTest
	@ValueSource(strings = { "uc-01", "1001", "uc-", "UC-1001", "uc-99999999999999999999", "" })
	void malformedCardIdIsNotFoundWithoutSaving(String userCardId) {
		assertThatThrownBy(() -> this.service.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.FIXED_REPRESENTATIVE, userCardId)))
			.isInstanceOf(UserCardNotFoundException.class);
		verifyNoInteractions(this.userCardService);
		verify(this.repository, never()).lockUserCard(anyLong());
		verify(this.repository, never()).upsert(anyLong(), anyString(), any(), any());
	}

	@Test
	void cardNotInUsersActiveCardsIsNotFound() {
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(confirmed("uc-1001", false)));

		assertThatThrownBy(() -> this.service.change(MY_CARDS,
				new PaymentPreferenceChange(PaymentSelectionMode.AUTO_RECOMMEND, "uc-1002")))
			.isInstanceOf(UserCardNotFoundException.class);
		verify(this.repository, never()).upsert(anyLong(), anyString(), any(), any());
	}

	@Test
	void ineligibleCardsAreRejectedWithoutSaving() {
		given(this.userCardService.list(MY_CARDS)).willReturn(List.of(confirmed("uc-1", true), mapping("uc-2",
				MappingStatus.UNMAPPED), mapping("uc-3", MappingStatus.NOT_SUPPORTED), synthetic("uc-4", true)));

		for (String id : List.of("uc-1", "uc-2", "uc-3", "uc-4")) {
			assertThatThrownBy(() -> this.service.change(MY_CARDS,
					new PaymentPreferenceChange(PaymentSelectionMode.AUTO_RECOMMEND, id)))
				.as(id)
				.isInstanceOf(RepresentativeCardNotEligibleException.class);
		}
		verify(this.repository, never()).upsert(anyLong(), anyString(), any(), any());
	}

	@Test
	void valueTypesRejectFixedModeWithoutCard() {
		assertThatThrownBy(() -> new PaymentPreferenceChange(PaymentSelectionMode.FIXED_REPRESENTATIVE, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PaymentPreferenceView(PaymentSelectionMode.FIXED_REPRESENTATIVE, null, NOW))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(PaymentPreferenceView.NOT_SET.updatedAt()).isNull();
	}

	private static UserCardView confirmed(String id, boolean excluded) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION, NOW),
				excluded, RecommendationEligibility.of(HoldingSource.MANUAL, MappingStatus.USER_CONFIRMED, excluded),
				NOW);
	}

	private static UserCardView mapping(String id, MappingStatus status) {
		return new UserCardView(id, "카드", HoldingSource.MANUAL, null,
				new UserCardMapping(status, null, ConfirmationMethod.MANUAL_SELECTION, NOW), false,
				RecommendationEligibility.of(HoldingSource.MANUAL, status, false), NOW);
	}

	private static UserCardView synthetic(String id, boolean excluded) {
		return new UserCardView(id, "카드", HoldingSource.SYNTHETIC, "09271", null, excluded,
				RecommendationEligibility.of(HoldingSource.SYNTHETIC, null, excluded), NOW);
	}

}
