package com.beone.api.card.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.beone.api.card.persistence.UserCardEntity;
import com.beone.api.card.persistence.UserCardRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUserProfile;
import com.beone.api.user.application.DemoUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 사용자의 보유 카드를 조회·등록·변경·해제한다(docs/api/cards.md, D-15·D-19·D-23).
 *
 * <p>쓰기 검사 순서는 가상 포트폴리오 409 → 카드 없음 404 → 상품 없음 404 → 중복 409다(요청 형식 400은 웹 계층, 결정 27 A2-9).
 * 다른 사용자·해제한 카드는 없는 카드와 같게 다룬다(D-09). 매핑 변경에 따른 추천 재계산은 추천(B2)이 생긴 뒤 연결한다.
 */
@Service
public class UserCardService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	// V4__user_cards.sql의 같은 상품 중복 금지 유니크 인덱스 이름.
	private static final String ACTIVE_PRODUCT_UNIQUE = "uk_user_cards_active_product";

	private final UserCardRepository userCardRepository;

	private final CardProductService cardProductService;

	private final DemoUserService demoUserService;

	private final RepresentativeCardGuard representativeCardGuard;

	private final Supplier<RecommendationInvalidationLookup> invalidationLookup;

	private final Clock clock;

	/**
	 * 대표 카드 확장 지점 빈이 없으면 {@link RepresentativeCardGuard#NONE}을, {@link Clock} 빈이 하나 있으면 그것을 쓴다.
	 * @param userCardRepository 보유 카드 저장소
	 * @param cardProductService 상품 존재 확인
	 * @param demoUserService 시연 사용자 표시 정보·읽기 전용 여부
	 * @param representativeCardGuard 결제 전 설정(A4)의 대표 카드 검사
	 * @param invalidationLookup 무효화된 추천 조회 포트. 추천이 보유 카드에 의존하므로 순환을 피하려고 부를 때 찾는다(#120)
	 * @param clock 등록·확인·해제 시각을 정할 시계
	 */
	@Autowired
	public UserCardService(UserCardRepository userCardRepository, CardProductService cardProductService,
			DemoUserService demoUserService, ObjectProvider<RepresentativeCardGuard> representativeCardGuard,
			ObjectProvider<RecommendationInvalidationLookup> invalidationLookup, ObjectProvider<Clock> clock) {
		this(userCardRepository, cardProductService, demoUserService,
				representativeCardGuard.getIfAvailable(() -> RepresentativeCardGuard.NONE),
				() -> invalidationLookup.getIfAvailable(() -> RecommendationInvalidationLookup.NONE),
				clock.getIfUnique(Clock::systemUTC));
	}

	UserCardService(UserCardRepository userCardRepository, CardProductService cardProductService,
			DemoUserService demoUserService, RepresentativeCardGuard representativeCardGuard,
			Supplier<RecommendationInvalidationLookup> invalidationLookup, Clock clock) {
		this.userCardRepository = Objects.requireNonNull(userCardRepository, "userCardRepository");
		this.cardProductService = Objects.requireNonNull(cardProductService, "cardProductService");
		this.demoUserService = Objects.requireNonNull(demoUserService, "demoUserService");
		this.representativeCardGuard = Objects.requireNonNull(representativeCardGuard, "representativeCardGuard");
		this.invalidationLookup = Objects.requireNonNull(invalidationLookup, "invalidationLookup");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/** 현재 사용자 표시 정보와 해제하지 않은 보유 카드 수. {@code codefConnected}는 1차에서 항상 {@code false}다. */
	@Transactional(readOnly = true)
	public CurrentUserSummary summarize(CurrentUser user) {
		return new CurrentUserSummary(profileOf(user),
				this.userCardRepository.countByUserIdAndReleasedAtIsNull(user.userId()), false);
	}

	/** 해제하지 않은 보유 카드를 등록 순으로 돌려준다(결정 27 A2-2). */
	@Transactional(readOnly = true)
	public List<UserCardView> list(CurrentUser user) {
		return this.userCardRepository.findByUserIdAndReleasedAtIsNullOrderByUserCardIdAsc(user.userId())
			.stream()
			.map(UserCardService::toView)
			.toList();
	}

	/**
	 * 보유 카드를 수동 등록한다.
	 * @throws PortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws CardProductNotFoundException 지원하지 않는 상품
	 * @throws UserCardDuplicatedException 같은 상품의 {@code USER_CONFIRMED} 카드가 이미 있음
	 */
	@Transactional
	public UserCardView register(CurrentUser user, NewUserCard card) {
		requireWritable(user);
		MappingStatus status = card.mappingStatus();
		if (status == MappingStatus.USER_CONFIRMED) {
			this.cardProductService.requireSupported(card.cardProductId());
			if (this.userCardRepository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(
					user.userId(), card.cardProductId(), MappingStatus.USER_CONFIRMED)) {
				throw new UserCardDuplicatedException();
			}
		}
		LocalDateTime now = now();
		boolean unmapped = status == MappingStatus.UNMAPPED;
		UserCardEntity entity = UserCardEntity.manual(user.userId(), card.displayName(), status, card.cardProductId(),
				unmapped ? null : ConfirmationMethod.MANUAL_SELECTION, unmapped ? null : now, now);
		return toView(saveGuardingDuplicate(entity));
	}

	/**
	 * 매핑을 확인·변경한다. 같은 카드를 같은 상품으로 다시 확인하면 확인 시각만 바뀐다(결정 27 A2-8).
	 * @throws PortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws UserCardNotFoundException 없거나 다른 사용자·해제한 카드
	 * @throws CandidateMismatchException {@code CODEF_CANDIDATE}인데 현재 후보와 다름
	 * @throws CardProductNotFoundException 지원하지 않는 상품
	 * @throws UserCardDuplicatedException 다른 카드가 이미 같은 상품으로 {@code USER_CONFIRMED}
	 */
	@Transactional
	public UserCardChangeResult changeMapping(CurrentUser user, String userCardId, MappingChange change) {
		requireWritable(user);
		UserCardEntity entity = find(user, userCardId);
		if (change.confirmationMethod() == ConfirmationMethod.CODEF_CANDIDATE) {
			// CANDIDATE는 2차 CODEF만 만든다. 1차 카드에는 후보가 없으므로 항상 일치하지 않는다(결정 27 A2-7).
			throw new CandidateMismatchException();
		}
		if (change.status() == MappingStatus.USER_CONFIRMED) {
			this.cardProductService.requireSupported(change.cardProductId());
			if (this.userCardRepository.existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNullAndUserCardIdNot(
					user.userId(), change.cardProductId(), MappingStatus.USER_CONFIRMED, entity.getUserCardId())) {
				throw new UserCardDuplicatedException();
			}
		}
		LocalDateTime confirmedAt = (change.status() == MappingStatus.UNMAPPED) ? null : now();
		RecommendationInvalidationLookup lookup = this.invalidationLookup.get();
		List<String> candidates = lookup.validUsing(user, entity.getUserCardId());
		entity.changeMapping(change.status(), change.cardProductId(), change.confirmationMethod(), confirmedAt);
		UserCardView saved = toView(saveGuardingDuplicate(entity));
		return new UserCardChangeResult(saved, lookup.invalidatedByMapping(user, candidates));
	}

	/**
	 * 표시 이름·추천 제외를 바꾼다. 추천 제외 값이 실제로 바뀔 때만 무효화 목록을 담는다(docs/api/cards.md).
	 * @throws PortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws UserCardNotFoundException 없거나 다른 사용자·해제한 카드
	 */
	@Transactional
	public UserCardChangeResult update(CurrentUser user, String userCardId, UserCardChange change) {
		requireWritable(user);
		UserCardEntity entity = find(user, userCardId);
		boolean exclusionChanged = change.excludedFromRecommendation() != null
				&& change.excludedFromRecommendation() != entity.isExcludedFromRecommendation();
		if (exclusionChanged && change.excludedFromRecommendation()) {
			this.representativeCardGuard.beforeExclusion(user, entity.getUserCardId());
		}
		RecommendationInvalidationLookup lookup = this.invalidationLookup.get();
		List<String> candidates = exclusionChanged ? lookup.validUsing(user, entity.getUserCardId()) : List.of();
		if (change.displayName() != null) {
			entity.rename(change.displayName());
		}
		if (exclusionChanged) {
			entity.changeExclusion(change.excludedFromRecommendation());
		}
		UserCardView saved = toView(this.userCardRepository.saveAndFlush(entity));
		return new UserCardChangeResult(saved, exclusionChanged ? lookup.invalidatedByMapping(user, candidates) : null);
	}

	/**
	 * 등록을 해제한다. 행을 지우지 않고 해제 시각을 남긴다. 이미 해제한 카드는 없는 카드다(D-23).
	 * @throws PortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws UserCardNotFoundException 없거나 다른 사용자·이미 해제한 카드
	 */
	@Transactional
	public void release(CurrentUser user, String userCardId) {
		requireWritable(user);
		UserCardEntity entity = find(user, userCardId);
		this.representativeCardGuard.beforeRelease(user, entity.getUserCardId());
		entity.release(now());
		this.userCardRepository.saveAndFlush(entity);
	}

	/**
	 * 모의 승인 전에 이 사용자의 해제하지 않은 보유 카드 행을 ID 오름차순으로 쓰기 잠금한다(#122). 추천 재계산은 이 카드들을 모두
	 * 비교하고 각 카드의 스냅샷 뒤 원장 차감액을 읽으므로, 같은 사용자의 다른 주문 승인은 앞 승인이 커밋할 때까지 기다린 뒤 그
	 * 원장을 반영해 계산한다. 잠금은 호출자 트랜잭션이 끝날 때 풀린다. 호출자의 트랜잭션 안에서만 부른다.
	 * @param user 현재 사용자
	 * @return 잠근 카드 수
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int lockHeldCardsForPayment(CurrentUser user) {
		return this.userCardRepository.lockActiveByUserIdOrderByUserCardId(user.userId()).size();
	}

	private void requireWritable(CurrentUser user) {
		if (profileOf(user).readOnly()) {
			throw new PortfolioReadOnlyException();
		}
	}

	private DemoUserProfile profileOf(CurrentUser user) {
		return this.demoUserService.listDemoUsers()
			.stream()
			.filter(profile -> profile.demoUser() == user.demoUser())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("시연 사용자 시드가 없습니다."));
	}

	private UserCardEntity find(CurrentUser user, String userCardId) {
		return UserCardId.parse(userCardId)
			.flatMap(id -> this.userCardRepository.findByUserCardIdAndUserIdAndReleasedAtIsNull(id, user.userId()))
			.orElseThrow(UserCardNotFoundException::new);
	}

	// 서비스 검사와 저장 사이에 같은 상품이 먼저 저장되면 DB 유니크(uk_user_cards_active_product)가 막는다. 그 제약 위반만 409로
	// 바꾸고, 다른 제약 위반은 원래 예외 그대로 둔다(500).
	private UserCardEntity saveGuardingDuplicate(UserCardEntity entity) {
		try {
			return this.userCardRepository.saveAndFlush(entity);
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesActiveProductUnique(ex)) {
				throw new UserCardDuplicatedException(ex);
			}
			throw ex;
		}
	}

	private static boolean violatesActiveProductUnique(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause().getMessage();
		return message != null && message.contains(ACTIVE_PRODUCT_UNIQUE);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
	}

	private static UserCardView toView(UserCardEntity entity) {
		boolean synthetic = entity.getHoldingSource() == HoldingSource.SYNTHETIC;
		UserCardMapping mapping = synthetic ? null : new UserCardMapping(entity.getMappingStatus(),
				entity.getCardProductId(), entity.getConfirmationMethod(), entity.getConfirmedAt());
		return new UserCardView(UserCardId.format(entity.getUserCardId()), entity.getDisplayName(),
				entity.getHoldingSource(), synthetic ? entity.getCardProductId() : null, mapping,
				entity.isExcludedFromRecommendation(),
				RecommendationEligibility.of(entity.getHoldingSource(), entity.getMappingStatus(),
						entity.isExcludedFromRecommendation()),
				entity.getRegisteredAt());
	}

}
