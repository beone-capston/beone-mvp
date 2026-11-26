package com.beone.api.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
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

import com.beone.api.card.persistence.UserCardEntity;
import com.beone.api.card.persistence.UserCardRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.PortfolioType;
import com.beone.api.user.application.UserProfile;
import com.beone.api.user.application.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 보유 카드 서비스의 판정·변환·검사 순서. 기대값은 docs/api/cards.md, D-15·D-19·D-23, 결정 27 A2절에서 옮겼다.
 *
 * <p>시계는 2026-11-17T01:30Z(서울 10:30)로 고정한다.
 */
class UserCardServiceTest {

	private static final CurrentUser MY_CARDS = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	/** 회원 장부(결정 37 D1a): 시연 사용자 구분이 없고 쓰기 가능하다. */
	private static final CurrentUser MEMBER = new CurrentUser(3L, null, PortfolioType.REAL, false);

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 30, 0);

	private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 11, 3, 10, 12, 0);

	private UserCardRepository repository;

	private CardProductService cardProductService;

	private RepresentativeCardGuard guard;

	private RecommendationInvalidationLookup invalidations;

	private CodefConnectionLookup connections;

	private UserCardService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(UserCardRepository.class);
		this.cardProductService = mock(CardProductService.class);
		this.guard = mock(RepresentativeCardGuard.class);
		this.invalidations = mock(RecommendationInvalidationLookup.class);
		this.connections = mock(CodefConnectionLookup.class);
		UserProfileService userProfileService = mock(UserProfileService.class);
		given(userProfileService.profileOf(MY_CARDS))
			.willReturn(new UserProfile(DemoUser.MY_CARDS, "내 카드", PortfolioType.REAL, false));
		given(userProfileService.profileOf(PORTFOLIO))
			.willReturn(new UserProfile(DemoUser.VIRTUAL_PORTFOLIO, "가상 포트폴리오", PortfolioType.SYNTHETIC, true));
		given(userProfileService.profileOf(MEMBER))
			.willReturn(new UserProfile(null, "내 카드", PortfolioType.REAL, false));
		given(this.repository.saveAndFlush(any())).willAnswer(invocation -> withId(invocation.getArgument(0), 1001L));
		this.service = new UserCardService(this.repository, this.cardProductService, userProfileService, this.guard,
				() -> this.invalidations, () -> this.connections,
				Clock.fixed(Instant.parse("2026-11-17T01:30:00Z"), ZoneOffset.UTC));
	}

	// 조회

	@Test
	void summarizeCombinesProfileAndActiveCardCount() {
		given(this.repository.countByUserIdAndReleasedAtIsNull(2L)).willReturn(4L);

		assertThat(this.service.summarize(PORTFOLIO)).isEqualTo(new CurrentUserSummary(
				new UserProfile(DemoUser.VIRTUAL_PORTFOLIO, "가상 포트폴리오", PortfolioType.SYNTHETIC, true), 4, false));
	}

	@Test
	void summarizeMemberLedgerHasNoDemoUser() {
		// 결정 37 D7: 회원 장부는 시연 사용자 구분 없이 '내 카드'·실제·쓰기 가능
		given(this.repository.countByUserIdAndReleasedAtIsNull(3L)).willReturn(1L);

		assertThat(this.service.summarize(MEMBER))
			.isEqualTo(new CurrentUserSummary(new UserProfile(null, "내 카드", PortfolioType.REAL, false), 1, false));
	}

	@Test
	void summarizeReportsCodefConnectionFromTheLookup() {
		// #136: 1차의 항상 false 대신 codef 연결 행 존재를 그대로 옮긴다
		given(this.repository.countByUserIdAndReleasedAtIsNull(1L)).willReturn(2L);
		given(this.connections.isConnected(MY_CARDS)).willReturn(true, false);

		assertThat(this.service.summarize(MY_CARDS).codefConnected()).isTrue();
		assertThat(this.service.summarize(MY_CARDS).codefConnected()).isFalse();
	}

	@Test
	void defaultLookupWithoutCodefIsNotConnected() {
		assertThat(CodefConnectionLookup.NONE.isConnected(MY_CARDS)).isFalse();
	}

	@Test
	void listMapsSyntheticAndManualCards() {
		UserCardEntity synthetic = synthetic(3L, "09174", "청춘대로 톡톡카드");
		UserCardEntity manual = withId(UserCardEntity.manual(1L, "회사 법인카드", MappingStatus.NOT_SUPPORTED, null,
				ConfirmationMethod.MANUAL_SELECTION, EARLIER, EARLIER), 1002L);
		given(this.repository.findByUserIdAndReleasedAtIsNullOrderByUserCardIdAsc(1L))
			.willReturn(List.of(synthetic, manual));

		List<UserCardView> cards = this.service.list(MY_CARDS);

		assertThat(cards).containsExactly(
				new UserCardView("uc-3", "청춘대로 톡톡카드", HoldingSource.SYNTHETIC, "09174", null, false,
						new RecommendationEligibility(true, null), EARLIER),
				new UserCardView("uc-1002", "회사 법인카드", HoldingSource.MANUAL, null,
						new UserCardMapping(MappingStatus.NOT_SUPPORTED, null, ConfirmationMethod.MANUAL_SELECTION,
								EARLIER),
						false, new RecommendationEligibility(false, EligibilityReason.NO_SUPPORTED_RULE), EARLIER));
		assertThat(cards.get(1).mapping().note()).isNull();
	}

	// 수동 등록

	@Test
	void registerWithProductIsUserConfirmedManualSelection() {
		UserCardView card = this.service.register(MY_CARDS, new NewUserCard("청춘대로 톡톡", "09174", false));

		verify(this.cardProductService).requireSupported("09174");
		assertThat(card).isEqualTo(new UserCardView("uc-1001", "청춘대로 톡톡", HoldingSource.MANUAL, null,
				new UserCardMapping(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION, NOW),
				false, new RecommendationEligibility(true, null), NOW));
		assertThat(card.mapping().note()).isEqualTo("사용자 확인 매핑, 공식 SKU·약관 버전 미검증");
		assertThat(saved().getUserId()).isEqualTo(1L);
	}

	@Test
	void registerNotSupportedAndUnmappedSkipProductAndDuplicateChecks() {
		UserCardView notSupported = this.service.register(MY_CARDS, new NewUserCard("회사 법인카드", null, true));
		UserCardView unmapped = this.service.register(MY_CARDS, new NewUserCard("모르는 카드", null, false));

		assertThat(notSupported.mapping()).isEqualTo(new UserCardMapping(MappingStatus.NOT_SUPPORTED, null,
				ConfirmationMethod.MANUAL_SELECTION, NOW));
		assertThat(unmapped.mapping()).isEqualTo(new UserCardMapping(MappingStatus.UNMAPPED, null, null, null));
		assertThat(unmapped.recommendationEligibility())
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.MAPPING_UNCONFIRMED));
		verifyNoInteractions(this.cardProductService);
		verify(this.repository, never()).existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(anyLong(),
				any(), any());
	}

	@Test
	void registerRejectsUnknownProductBeforeDuplicateCheck() {
		willThrow(new CardProductNotFoundException()).given(this.cardProductService).requireSupported("9174");

		assertThatThrownBy(() -> this.service.register(MY_CARDS, new NewUserCard("카드", "9174", false)))
			.isInstanceOf(CardProductNotFoundException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void registerRejectsSecondUserConfirmedCardOfSameProduct() {
		given(this.repository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(1L, "09174",
				MappingStatus.USER_CONFIRMED))
			.willReturn(true);

		assertThatThrownBy(() -> this.service.register(MY_CARDS, new NewUserCard("카드", "09174", false)))
			.isInstanceOf(UserCardDuplicatedException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void registerTurnsOnlyActiveProductUniqueViolationIntoDuplicate() {
		DataIntegrityViolationException unique = new DataIntegrityViolationException("insert failed",
				new java.sql.SQLIntegrityConstraintViolationException(
						"Duplicate entry for key 'user_cards.uk_user_cards_active_product'"));
		willThrow(unique).given(this.repository).saveAndFlush(any());

		assertThatThrownBy(() -> this.service.register(MY_CARDS, new NewUserCard("카드", "09174", false)))
			.isInstanceOf(UserCardDuplicatedException.class)
			.hasCause(unique);
	}

	@Test
	void otherConstraintViolationsAreRethrownUnchanged() {
		DataIntegrityViolationException foreignKey = new DataIntegrityViolationException("insert failed",
				new java.sql.SQLIntegrityConstraintViolationException("foreign key constraint fk_user_cards_user fails"));
		willThrow(foreignKey).given(this.repository).saveAndFlush(any());

		assertThatThrownBy(() -> this.service.register(MY_CARDS, new NewUserCard("카드", "09174", false)))
			.isSameAs(foreignKey);
		assertThatThrownBy(() -> this.service.register(MY_CARDS, new NewUserCard("카드", null, false)))
			.isSameAs(foreignKey);
	}

	@Test
	void virtualPortfolioCannotWriteAndIsCheckedFirst() {
		assertThatThrownBy(() -> this.service.register(PORTFOLIO, new NewUserCard("카드", "09174", false)))
			.isInstanceOf(PortfolioReadOnlyException.class);
		assertThatThrownBy(() -> this.service.changeMapping(PORTFOLIO, "not-an-id",
				new MappingChange(MappingStatus.UNMAPPED, null, null)))
			.isInstanceOf(PortfolioReadOnlyException.class);
		assertThatThrownBy(() -> this.service.update(PORTFOLIO, "uc-1", new UserCardChange("카드", null)))
			.isInstanceOf(PortfolioReadOnlyException.class);
		assertThatThrownBy(() -> this.service.release(PORTFOLIO, "uc-1"))
			.isInstanceOf(PortfolioReadOnlyException.class);
		verifyNoInteractions(this.repository, this.cardProductService, this.guard);
	}

	// 매핑 확인·변경

	@Test
	void changeMappingConfirmsProductAndReturnsEmptyInvalidations() {
		givenCard(unmappedCard(1001L));

		UserCardChangeResult result = this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION));

		verify(this.cardProductService).requireSupported("09174");
		verify(this.repository).existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNullAndUserCardIdNot(1L,
				"09174", MappingStatus.USER_CONFIRMED, 1001L);
		assertThat(result.card().mapping()).isEqualTo(new UserCardMapping(MappingStatus.USER_CONFIRMED, "09174",
				ConfirmationMethod.MANUAL_SELECTION, NOW));
		assertThat(result.invalidatedRecommendationIds()).isEmpty();
	}

	@Test
	void changeMappingToUnmappedClearsConfirmation() {
		givenCard(withId(UserCardEntity.manual(1L, "카드", MappingStatus.USER_CONFIRMED, "09174",
				ConfirmationMethod.MANUAL_SELECTION, EARLIER, EARLIER), 1001L));

		UserCardChangeResult result = this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.UNMAPPED, null, null));

		assertThat(result.card().mapping()).isEqualTo(new UserCardMapping(MappingStatus.UNMAPPED, null, null, null));
		verifyNoInteractions(this.cardProductService);
	}

	@Test
	void changeMappingRejectsCodefCandidateWhenTheCardHasNoCandidate() {
		givenCard(unmappedCard(1001L));

		assertThatThrownBy(() -> this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.CODEF_CANDIDATE)))
			.isInstanceOf(CandidateMismatchException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	// CODEF 이름 사전 후보(#268)

	@Test
	void codefCandidateConfirmationTurnsTheCandidateIntoUserConfirmed() {
		givenCard(candidateCard(1001L, "09271"));
		given(this.invalidations.validUsing(MY_CARDS, 1001L)).willReturn(List.of());
		given(this.invalidations.invalidatedByMapping(MY_CARDS, List.of())).willReturn(List.of());

		UserCardChangeResult result = this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09271", ConfirmationMethod.CODEF_CANDIDATE));

		assertThat(result.card().mapping()).isEqualTo(new UserCardMapping(MappingStatus.USER_CONFIRMED, "09271",
				ConfirmationMethod.CODEF_CANDIDATE, NOW, null));
		assertThat(saved().getCandidateCardProductId()).isNull();
		assertThat(saved().isMappingChangedByUser()).isTrue();
	}

	@Test
	void codefCandidateConfirmationRejectsAnotherProduct() {
		givenCard(candidateCard(1001L, "09271"));

		assertThatThrownBy(() -> this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.CODEF_CANDIDATE)))
			.isInstanceOf(CandidateMismatchException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void manualSelectionOnACandidateClearsTheCandidate() {
		givenCard(candidateCard(1001L, "09271"));

		UserCardChangeResult result = this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.UNMAPPED, null, null));

		assertThat(result.card().mapping()).isEqualTo(new UserCardMapping(MappingStatus.UNMAPPED, null, null, null,
				null));
		assertThat(saved().isMappingChangedByUser()).isTrue();
	}

	@Test
	void listShowsTheCandidateProductOnlyForCandidates() {
		given(this.repository.findByUserIdAndReleasedAtIsNullOrderByUserCardIdAsc(1L))
			.willReturn(List.of(candidateCard(1001L, "01664"), codefCard(1002L)));

		List<UserCardView> cards = this.service.list(MY_CARDS);

		assertThat(cards.get(0).mapping()).isEqualTo(new UserCardMapping(MappingStatus.CANDIDATE, null, null, null,
				"01664"));
		assertThat(cards.get(0).recommendationEligibility().reason()).isEqualTo(EligibilityReason.MAPPING_UNCONFIRMED);
		assertThat(cards.get(1).mapping().candidateCardProductId()).isNull();
	}

	@Test
	void registerCodefCardWithACandidateIsCandidateNotUserConfirmed() {
		RegisteredCodefCard registered = this.service.registerCodefCard(MY_CARDS, "올라운드 티타늄카드", "09271");

		assertThat(registered).isEqualTo(new RegisteredCodefCard(1001L, "09271"));
		assertThat(registered.mappingStatus()).isEqualTo(MappingStatus.CANDIDATE);
		UserCardEntity entity = saved();
		assertThat(entity.getMappingStatus()).isEqualTo(MappingStatus.CANDIDATE);
		assertThat(entity.getCandidateCardProductId()).isEqualTo("09271");
		assertThat(entity.getCardProductId()).isNull();
		assertThat(entity.getConfirmationMethod()).isNull();
		assertThat(entity.getConfirmedAt()).isNull();
		assertThat(entity.isMappingChangedByUser()).isFalse();
	}

	@Test
	void registerCodefCardWithoutCandidateIsUnmapped() {
		RegisteredCodefCard registered = this.service.registerCodefCard(MY_CARDS, "가짜 카드", null);

		assertThat(registered).isEqualTo(new RegisteredCodefCard(1001L, null));
		assertThat(saved().getMappingStatus()).isEqualTo(MappingStatus.UNMAPPED);
		verify(this.repository, never()).existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(anyLong(),
				any(), any());
	}

	@Test
	void registerCodefCardSkipsTheCandidateWhenTheProductIsAlreadyConfirmed() {
		// 이미 확인한 상품을 후보로 두면 사용자가 확인할 때 409가 되므로 UNMAPPED로 둔다
		given(this.repository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(1L, "09271",
				MappingStatus.USER_CONFIRMED))
			.willReturn(true);

		RegisteredCodefCard registered = this.service.registerCodefCard(MY_CARDS, "올라운드 티타늄카드", "09271");

		assertThat(registered.candidateCardProductId()).isNull();
		assertThat(saved().getMappingStatus()).isEqualTo(MappingStatus.UNMAPPED);
		assertThat(saved().getCandidateCardProductId()).isNull();
	}

	@Test
	void proposeCodefCandidateFillsAnUntouchedUnmappedCodefCard() {
		givenCard(codefCard(1001L));

		assertThat(this.service.proposeCodefCandidate(MY_CARDS, 1001L, "01914")).isTrue();

		UserCardEntity entity = saved();
		assertThat(entity.getMappingStatus()).isEqualTo(MappingStatus.CANDIDATE);
		assertThat(entity.getCandidateCardProductId()).isEqualTo("01914");
	}

	@Test
	void proposeCodefCandidateLeavesProtectedOrChangedCardsAlone() {
		UserCardEntity confirmed = codefCard(1001L);
		confirmed.changeMapping(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION, EARLIER);
		UserCardEntity notSupported = codefCard(1002L);
		notSupported.changeMapping(MappingStatus.NOT_SUPPORTED, null, ConfirmationMethod.MANUAL_SELECTION, EARLIER);
		UserCardEntity userUnmapped = codefCard(1003L);
		userUnmapped.changeMapping(MappingStatus.UNMAPPED, null, null, null);
		UserCardEntity candidate = candidateCard(1004L, "09271");
		UserCardEntity manual = unmappedCard(1005L);
		for (UserCardEntity card : List.of(confirmed, notSupported, userUnmapped, candidate, manual)) {
			givenCard(card);
		}

		for (long id = 1001L; id <= 1005L; id++) {
			assertThat(this.service.proposeCodefCandidate(MY_CARDS, id, "01914")).as("uc-%d", id).isFalse();
		}
		assertThat(this.service.proposeCodefCandidate(MY_CARDS, 9999L, "01914")).isFalse();
		verify(this.repository, never()).saveAndFlush(any());
		assertThat(confirmed.getMappingStatus()).isEqualTo(MappingStatus.USER_CONFIRMED);
		assertThat(notSupported.getMappingStatus()).isEqualTo(MappingStatus.NOT_SUPPORTED);
		assertThat(userUnmapped.getMappingStatus()).isEqualTo(MappingStatus.UNMAPPED);
		assertThat(candidate.getCandidateCardProductId()).isEqualTo("09271");
	}

	@Test
	void proposeCodefCandidateSkipsAProductTheUserAlreadyConfirmed() {
		givenCard(codefCard(1001L));
		given(this.repository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(1L, "01914",
				MappingStatus.USER_CONFIRMED))
			.willReturn(true);

		assertThat(this.service.proposeCodefCandidate(MY_CARDS, 1001L, "01914")).isFalse();
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void changeMappingRejectsProductAlreadyConfirmedOnAnotherCard() {
		givenCard(unmappedCard(1001L));
		given(this.repository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNullAndUserCardIdNot(1L,
				"09174", MappingStatus.USER_CONFIRMED, 1001L))
			.willReturn(true);

		assertThatThrownBy(() -> this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION)))
			.isInstanceOf(UserCardDuplicatedException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void missingMalformedOrOtherUsersCardIsNotFound() {
		MappingChange change = new MappingChange(MappingStatus.UNMAPPED, null, null);
		for (String id : List.of("uc-404", "1001", "uc-01001")) {
			assertThatThrownBy(() -> this.service.changeMapping(MY_CARDS, id, change))
				.isInstanceOf(UserCardNotFoundException.class);
			assertThatThrownBy(() -> this.service.update(MY_CARDS, id, new UserCardChange(null, true)))
				.isInstanceOf(UserCardNotFoundException.class);
			assertThatThrownBy(() -> this.service.release(MY_CARDS, id)).isInstanceOf(UserCardNotFoundException.class);
		}
		verify(this.repository, never()).findByUserCardIdAndUserIdAndReleasedAtIsNull(any(), eq(2L));
	}

	// 표시 이름·추천 제외

	@Test
	void excludingCardAsksGuardFirstAndReturnsEmptyInvalidations() {
		givenCard(confirmedCard(1001L));

		UserCardChangeResult result = this.service.update(MY_CARDS, "uc-1001", new UserCardChange("새 이름", true));

		InOrder order = inOrder(this.guard, this.repository);
		order.verify(this.guard).beforeExclusion(MY_CARDS, 1001L);
		order.verify(this.repository).saveAndFlush(any());
		assertThat(result.card().displayName()).isEqualTo("새 이름");
		assertThat(result.card().excludedFromRecommendation()).isTrue();
		assertThat(result.card().recommendationEligibility())
			.isEqualTo(new RecommendationEligibility(false, EligibilityReason.USER_EXCLUDED));
		assertThat(result.invalidatedRecommendationIds()).isEmpty();
	}

	@Test
	void renamingOrUnchangedExclusionDoesNotInvalidate() {
		givenCard(confirmedCard(1001L));

		UserCardChangeResult rename = this.service.update(MY_CARDS, "uc-1001", new UserCardChange("새 이름", null));
		UserCardChangeResult same = this.service.update(MY_CARDS, "uc-1001", new UserCardChange(null, false));

		assertThat(rename.invalidatedRecommendationIds()).isNull();
		assertThat(same.invalidatedRecommendationIds()).isNull();
		verifyNoInteractions(this.guard);
	}

	@Test
	void includingCardAgainInvalidatesWithoutGuard() {
		UserCardEntity card = confirmedCard(1001L);
		card.changeExclusion(true);
		givenCard(card);

		UserCardChangeResult result = this.service.update(MY_CARDS, "uc-1001", new UserCardChange(null, false));

		assertThat(result.invalidatedRecommendationIds()).isEmpty();
		assertThat(result.card().excludedFromRecommendation()).isFalse();
		verifyNoInteractions(this.guard);
	}

	@Test
	void guardRejectionStopsExclusion() {
		givenCard(confirmedCard(1001L));
		willThrow(new IllegalStateException("representative")).given(this.guard).beforeExclusion(MY_CARDS, 1001L);

		assertThatThrownBy(() -> this.service.update(MY_CARDS, "uc-1001", new UserCardChange(null, true)))
			.isInstanceOf(IllegalStateException.class);
		verify(this.repository, never()).saveAndFlush(any());
	}

	@Test
	void mappingChangeListsRecommendationsThatWereValidBeforeAndAreNowInvalidated() {
		// docs/api/cards.md 415행: 변경 전 유효 목록을 저장 전에 받고, 저장 뒤 그중 MAPPING_CHANGED가 된 것만 돌려준다(#120)
		givenCard(unmappedCard(1001L));
		given(this.invalidations.validUsing(MY_CARDS, 1001L)).willReturn(List.of("rec-1", "rec-2"));
		given(this.invalidations.invalidatedByMapping(MY_CARDS, List.of("rec-1", "rec-2"))).willReturn(List.of("rec-2"));

		UserCardChangeResult result = this.service.changeMapping(MY_CARDS, "uc-1001",
				new MappingChange(MappingStatus.USER_CONFIRMED, "09174", ConfirmationMethod.MANUAL_SELECTION));

		InOrder order = inOrder(this.invalidations, this.repository);
		order.verify(this.invalidations).validUsing(MY_CARDS, 1001L);
		order.verify(this.repository).saveAndFlush(any());
		order.verify(this.invalidations).invalidatedByMapping(MY_CARDS, List.of("rec-1", "rec-2"));
		assertThat(result.invalidatedRecommendationIds()).containsExactly("rec-2");
	}

	@Test
	void actualExclusionChangeListsInvalidatedRecommendationsButRenameDoesNotAsk() {
		givenCard(confirmedCard(1001L));
		given(this.invalidations.validUsing(MY_CARDS, 1001L)).willReturn(List.of("rec-7"));
		given(this.invalidations.invalidatedByMapping(MY_CARDS, List.of("rec-7"))).willReturn(List.of("rec-7"));

		UserCardChangeResult rename = this.service.update(MY_CARDS, "uc-1001", new UserCardChange("새 이름", null));
		verifyNoInteractions(this.invalidations);
		UserCardChangeResult excluded = this.service.update(MY_CARDS, "uc-1001", new UserCardChange(null, true));

		assertThat(rename.invalidatedRecommendationIds()).isNull();
		assertThat(excluded.invalidatedRecommendationIds()).containsExactly("rec-7");
	}

	@Test
	void defaultInvalidationLookupFindsNothing() {
		assertThat(RecommendationInvalidationLookup.NONE.validUsing(MY_CARDS, 1001L)).isEmpty();
		assertThat(RecommendationInvalidationLookup.NONE.invalidatedByMapping(MY_CARDS, List.of("rec-1"))).isEmpty();
	}

	// 해제

	@Test
	void releaseAsksGuardAndKeepsRowWithReleaseTime() {
		givenCard(confirmedCard(1001L));

		this.service.release(MY_CARDS, "uc-1001");

		InOrder order = inOrder(this.guard, this.repository);
		order.verify(this.guard).beforeRelease(MY_CARDS, 1001L);
		order.verify(this.repository).saveAndFlush(any());
		assertThat(saved().getReleasedAt()).isEqualTo(NOW);
		verify(this.repository, never()).delete(any());
	}

	@Test
	void paymentLockLocksEveryActiveCardOfTheUserInIdOrder() {
		// #122: 모의 승인이 재계산 전에 이 사용자의 해제하지 않은 카드 행 전부를 ID 오름차순 잠금 조회로 잠근다
		given(this.repository.lockActiveByUserIdOrderByUserCardId(1L))
			.willReturn(List.of(confirmedCard(1001L), unmappedCard(1003L)));

		assertThat(this.service.lockHeldCardsForPayment(MY_CARDS)).isEqualTo(2);
		verify(this.repository).lockActiveByUserIdOrderByUserCardId(1L);
	}

	@Test
	void defaultGuardBlocksNothing() {
		RepresentativeCardGuard.NONE.beforeExclusion(MY_CARDS, 1L);
		RepresentativeCardGuard.NONE.beforeRelease(MY_CARDS, 1L);
	}

	private UserCardEntity saved() {
		ArgumentCaptor<UserCardEntity> captor = ArgumentCaptor.forClass(UserCardEntity.class);
		verify(this.repository, atLeastOnce()).saveAndFlush(captor.capture());
		return captor.getValue();
	}

	private void givenCard(UserCardEntity card) {
		given(this.repository.findByUserCardIdAndUserIdAndReleasedAtIsNull(card.getUserCardId(), 1L))
			.willReturn(Optional.of(card));
	}

	private static UserCardEntity unmappedCard(long id) {
		return withId(UserCardEntity.manual(1L, "카드", MappingStatus.UNMAPPED, null, null, null, EARLIER), id);
	}

	private static UserCardEntity codefCard(long id) {
		return withId(UserCardEntity.codef(1L, "카드", null, EARLIER), id);
	}

	private static UserCardEntity candidateCard(long id, String candidateCardProductId) {
		return withId(UserCardEntity.codef(1L, "카드", candidateCardProductId, EARLIER), id);
	}

	private static UserCardEntity confirmedCard(long id) {
		return withId(UserCardEntity.manual(1L, "카드", MappingStatus.USER_CONFIRMED, "09174",
				ConfirmationMethod.MANUAL_SELECTION, EARLIER, EARLIER), id);
	}

	private static UserCardEntity synthetic(long id, String cardProductId, String name) {
		UserCardEntity entity = withId(UserCardEntity.manual(2L, name, null, null, null, null, EARLIER), id);
		ReflectionTestUtils.setField(entity, "holdingSource", HoldingSource.SYNTHETIC);
		ReflectionTestUtils.setField(entity, "cardProductId", cardProductId);
		return entity;
	}

	private static UserCardEntity withId(UserCardEntity entity, long id) {
		if (entity.getUserCardId() == null) {
			ReflectionTestUtils.setField(entity, "userCardId", id);
		}
		return entity;
	}

}
