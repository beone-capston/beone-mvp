package com.beone.api.codef.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.card.application.RegisteredCodefCard;
import com.beone.api.card.application.UserCardService;
import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.persistence.CodefApprovalRowEntity;
import com.beone.api.codef.persistence.CodefApprovalRowRepository;
import com.beone.api.codef.persistence.CodefConnectionEntity;
import com.beone.api.codef.persistence.CodefConnectionRepository;
import com.beone.api.codef.persistence.CodefHeldCardEntity;
import com.beone.api.codef.persistence.CodefHeldCardRepository;
import com.beone.api.codef.persistence.CodefPerformanceReferenceEntity;
import com.beone.api.codef.persistence.CodefPerformanceReferenceRepository;
import com.beone.api.codef.persistence.CodefSyncEntity;
import com.beone.api.codef.persistence.CodefSyncRepository;
import com.beone.api.codef.sync.CodefApprovalFingerprint;
import com.beone.api.codef.sync.CodefApprovalFingerprinter;
import com.beone.api.codef.sync.CodefApprovalKey;
import com.beone.api.codef.sync.CodefCardLink;
import com.beone.api.codef.sync.CodefCardLinker;
import com.beone.api.codef.sync.CodefHeldCardCandidate;
import com.beone.api.codef.sync.CodefIncompleteReason;
import com.beone.api.codef.sync.CodefLedgerCandidateBuilder;
import com.beone.api.codef.sync.CodefLedgerCandidateRow;
import com.beone.api.codef.sync.CodefLedgerCandidates;
import com.beone.api.codef.sync.CodefLedgerRowKind;
import com.beone.api.codef.sync.CodefPerformanceLink;
import com.beone.api.codef.sync.CodefPerformanceReference;
import com.beone.api.codef.sync.CodefTargetOutcome;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.user.application.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CODEF 보유카드·승인내역·실적을 조회해 BeONE에 옮긴다(docs/api/codef.md POST /api/v1/me/codef/syncs, D-15·D-16·D-17, 결정 20·22,
 * 관리자 판단 K1·K2·P6·P8·P9·P11·Q1~Q8).
 *
 * <p>순서: (요청 본문·날짜 형식 400은 컨트롤러 바인딩에서 먼저) 가상 포트폴리오 409 → 기간 400 → 연결 없음 409 → 재연결 필요 409 → 게이트웨이·connectedId 키 없음 502 → CODEF 조회(트랜잭션 밖,
 * 동기) → 세 대상 모두 실패면 502 → 짧은 저장 트랜잭션. 저장 트랜잭션은 보유 카드 행(ID 오름차순) → 사용자 행 → 연결 행 순으로 잠그고,
 * 조회 중 연결이 해제·교체됐으면 아무것도 저장하지 않고 409 {@code CODEF_NOT_CONNECTED}다.
 *
 * <p>보유카드는 {@link CodefHeldCardMatcher}로 맞춰 새 카드만 보유 카드로 만든다. 카드명이 {@link CodefCardNameDictionary}와 정확히
 * 일치하면 {@code CANDIDATE}와 후보 상품을, 아니면 {@code UNMAPPED}를 둔다. 다시 대조된 기존 카드도 후보 없는 {@code UNMAPPED}면
 * 같은 사전으로 후보를 둔다. 후보는 상품당 1장이라 응답 순서로 먼저 처리한 카드만 받는다(판정은 보유 카드 서비스). {@code USER_CONFIRMED}로는
 * 확정하지 않는다(D-15 2026-11-26 변경, #268). 승인내역은
 * {@link CodefLedgerCandidateBuilder}로 판정해 받아들인 행만 codef 전용 테이블에 넣고 원장({@code ledger_transactions})은 바꾸지 않는다(R8).
 * 불완전 행은 저장하지 않으며 다음 동기화에서 다시 판정한다. 승인번호 HMAC 키가 없으면 승인내역을 조회·저장하지 않고 부분 실패로 알린다(P6).
 * 실적은 참고값으로만 저장하고 계산·월간 상태에 넣지 않는다(D-16). 원장이 완전하다고 선언하지 않는다(R7).
 */
@Service
public class CodefSyncService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final CodefConnectionRepository connections;

	private final CodefSyncRepository syncs;

	private final CodefHeldCardRepository heldCards;

	private final CodefApprovalRowRepository approvalRows;

	private final CodefPerformanceReferenceRepository performanceReferences;

	private final UserCardService userCardService;

	private final ObjectProvider<CodefSyncGateway> gateway;

	private final ConnectedIdKeySource connectedIdKeySource;

	private final ApprovalFingerprintKeySource fingerprintKeySource;

	private final TransactionTemplate transactions;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param connections 연결 저장소
	 * @param syncs 동기화 기록 저장소
	 * @param heldCards CODEF 카드 대조 정보 저장소
	 * @param approvalRows CODEF 승인 행 저장소
	 * @param performanceReferences 실적 참고값 저장소
	 * @param userCardService 보유 카드 잠금·CODEF 카드 생성
	 * @param gateway CODEF 조회 게이트웨이. 없을 수 있다
	 * @param connectedIdKeySource connectedId 복호화 키 공급
	 * @param fingerprintKeySource 승인번호 HMAC 키 공급
	 * @param transactionManager 저장 트랜잭션
	 * @param clock 기간·조회 시각을 정할 시계
	 */
	@Autowired
	public CodefSyncService(CodefConnectionRepository connections, CodefSyncRepository syncs,
			CodefHeldCardRepository heldCards, CodefApprovalRowRepository approvalRows,
			CodefPerformanceReferenceRepository performanceReferences, UserCardService userCardService,
			ObjectProvider<CodefSyncGateway> gateway, ConnectedIdKeySource connectedIdKeySource,
			ApprovalFingerprintKeySource fingerprintKeySource, PlatformTransactionManager transactionManager,
			ObjectProvider<Clock> clock) {
		this.connections = Objects.requireNonNull(connections, "connections");
		this.syncs = Objects.requireNonNull(syncs, "syncs");
		this.heldCards = Objects.requireNonNull(heldCards, "heldCards");
		this.approvalRows = Objects.requireNonNull(approvalRows, "approvalRows");
		this.performanceReferences = Objects.requireNonNull(performanceReferences, "performanceReferences");
		this.userCardService = Objects.requireNonNull(userCardService, "userCardService");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.connectedIdKeySource = Objects.requireNonNull(connectedIdKeySource, "connectedIdKeySource");
		this.fingerprintKeySource = Objects.requireNonNull(fingerprintKeySource, "fingerprintKeySource");
		this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
		this.clock = clock.getIfUnique(Clock::systemUTC).withZone(SEOUL);
	}

	/**
	 * 동기화한다.
	 * @param user 현재 사용자
	 * @param from 요청 시작일. 없으면 {@code null}
	 * @param to 요청 종료일. 없으면 {@code null}
	 * @return 결과
	 * @throws CodefPortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws CodefSyncPeriodException 기간 규칙 위반
	 * @throws CodefNotConnectedException 연결 없음, 조회 중 해제·교체
	 * @throws CodefReconnectRequiredException 재연결 필요
	 * @throws CodefUnavailableException 게이트웨이·키 없음, 세 대상 모두 실패
	 */
	public CodefSyncResult sync(CurrentUser user, LocalDate from, LocalDate to) {
		if (user.readOnly()) {
			throw new CodefPortfolioReadOnlyException();
		}
		CodefSyncPeriod period = CodefSyncPeriod.resolve(from, to, LocalDate.now(this.clock));
		Connection connection = this.transactions.execute(status -> this.connections.findById(user.userId())
			.map(entity -> new Connection(entity.getOrganization(), entity.getConnectedIdCiphertext(),
					entity.getStatus()))
			.orElseThrow(CodefNotConnectedException::new));
		if (connection.status() == CodefConnectionStatus.RECONNECT_REQUIRED) {
			throw new CodefReconnectRequiredException();
		}
		CodefSyncGateway codef = this.gateway.getIfAvailable();
		Optional<byte[]> connectedIdKey = this.connectedIdKeySource.key();
		if (codef == null || connectedIdKey.isEmpty()) {
			throw new CodefUnavailableException();
		}
		String connectedId = decrypt(connectedIdKey.get(), user.userId(), connection.ciphertext());
		Optional<CodefApprovalFingerprinter> fingerprinter = this.fingerprintKeySource.key()
			.map(HmacApprovalFingerprinter::new);
		Fetched fetched;
		try {
			fetched = fetch(codef, connection.organization(), connectedId, period, fingerprinter.isPresent());
		}
		catch (CodefConnectionInvalidException ex) {
			markReconnectRequired(user, connection.ciphertext());
			throw new CodefReconnectRequiredException();
		}
		Instant checkedAt = this.clock.instant();
		CodefTargetOutcome<CodefHeldCardCandidate> heldOutcome = CodefTargetOutcome.judge(fetched.heldCards(),
				checkedAt, CodefHeldCardCandidate::from);
		Optional<CodefTargetOutcome<CodefApproval>> approvalOutcome = Optional.ofNullable(fetched.approvals())
			.map(call -> CodefTargetOutcome.judge(call, checkedAt, approval -> approval));
		CodefTargetOutcome<CodefPerformanceReference> performanceOutcome = CodefTargetOutcome
			.judge(fetched.performances(), checkedAt, card -> CodefPerformanceReference.from(card, checkedAt));
		List<CodefSyncTarget> failures = failures(heldOutcome, approvalOutcome, performanceOutcome);
		if (failures.size() == CodefSyncTarget.values().length) {
			throw new CodefUnavailableException();
		}
		LocalDateTime syncedAt = LocalDateTime.ofInstant(checkedAt, SEOUL).truncatedTo(ChronoUnit.SECONDS);
		return this.transactions.execute(status -> store(user, connection, period, syncedAt, heldOutcome,
				approvalOutcome, performanceOutcome, fingerprinter, failures));
	}

	private static String decrypt(byte[] key, long userId, String ciphertext) {
		try {
			return new ConnectedIdCipher(key).decrypt(userId, ciphertext);
		}
		catch (RuntimeException ex) {
			// 키 교체 등으로 복호화하지 못하면 CODEF를 부르지 않는다. 값은 남기지 않는다
			throw new CodefUnavailableException();
		}
	}

	// 승인번호 HMAC 키가 없으면 승인내역은 부르지 않는다(과금 회피, P6)
	private static Fetched fetch(CodefSyncGateway codef, String organization, String connectedId,
			CodefSyncPeriod period, boolean approvals) {
		CodefCallResult<CodefHeldCard> held = codef.heldCards(organization, connectedId);
		CodefCallResult<CodefApproval> approvalCall = approvals
				? codef.approvals(organization, connectedId, period.from(), period.to()) : null;
		CodefCallResult<CodefCardPerformance> performances = codef.performances(organization, connectedId);
		return new Fetched(held, approvalCall, performances);
	}

	private void markReconnectRequired(CurrentUser user, String ciphertext) {
		this.transactions.executeWithoutResult(status -> {
			this.connections.lockUser(user.userId());
			this.connections.lockByUserId(user.userId())
				.filter(entity -> entity.getConnectedIdCiphertext().equals(ciphertext))
				.ifPresent(CodefConnectionEntity::markReconnectRequired);
		});
	}

	static List<CodefSyncTarget> failures(CodefTargetOutcome<?> held, Optional<? extends CodefTargetOutcome<?>> approvals,
			CodefTargetOutcome<?> performances) {
		List<CodefSyncTarget> failures = new ArrayList<>();
		if (held instanceof CodefTargetOutcome.Unusable<?>) {
			failures.add(CodefSyncTarget.HELD_CARDS);
		}
		if (approvals.isEmpty() || approvals.get() instanceof CodefTargetOutcome.Unusable<?>) {
			failures.add(CodefSyncTarget.APPROVALS);
		}
		if (performances instanceof CodefTargetOutcome.Unusable<?>) {
			failures.add(CodefSyncTarget.PERFORMANCES);
		}
		return List.copyOf(failures);
	}

	private CodefSyncResult store(CurrentUser user, Connection connection, CodefSyncPeriod period,
			LocalDateTime syncedAt, CodefTargetOutcome<CodefHeldCardCandidate> heldOutcome,
			Optional<CodefTargetOutcome<CodefApproval>> approvalOutcome,
			CodefTargetOutcome<CodefPerformanceReference> performanceOutcome,
			Optional<CodefApprovalFingerprinter> fingerprinter, List<CodefSyncTarget> failures) {
		List<Long> activeCodefCards = this.userCardService.lockHeldCardsForCodefSync(user);
		this.connections.lockUser(user.userId());
		CodefConnectionEntity current = this.connections.lockByUserId(user.userId())
			.filter(entity -> entity.getConnectedIdCiphertext().equals(connection.ciphertext()))
			.orElseThrow(CodefNotConnectedException::new);
		long syncId = this.syncs
			.save(new CodefSyncEntity(user.userId(), period.from(), period.to(), syncedAt))
			.getSyncId();
		HeldCardStep held = storeHeldCards(user, syncId, heldOutcome, activeCodefCards);
		CodefSyncResult.Approvals approvals = (approvalOutcome.isPresent()
				&& approvalOutcome.get() instanceof CodefTargetOutcome.Usable<CodefApproval> usable)
						? storeApprovals(user, syncId, syncedAt, usable.items(), CodefCardLinker.of(heldOutcome),
								held.userCardIds(), fingerprinter.orElseThrow())
						: new CodefSyncResult.Approvals(null, null, List.of());
		List<CodefSyncResult.PerformanceItem> performances = storePerformances(user, syncId, syncedAt,
				performanceOutcome, CodefCardLinker.of(heldOutcome), held.userCardIds());
		current.markSynced(syncedAt);
		return new CodefSyncResult(syncId, syncedAt, held.result(), approvals, performances, failures);
	}

	private HeldCardStep storeHeldCards(CurrentUser user, long syncId,
			CodefTargetOutcome<CodefHeldCardCandidate> outcome, List<Long> activeCodefCards) {
		if (!(outcome instanceof CodefTargetOutcome.Usable<CodefHeldCardCandidate> usable)) {
			return new HeldCardStep(new CodefSyncResult.HeldCards(null, List.of()), Map.of());
		}
		List<CodefHeldCardMatcher.ExistingCard> existing = activeCodefCards.isEmpty() ? List.of()
				: this.heldCards.findByUserIdAndUserCardIdIn(user.userId(), activeCodefCards)
					.stream()
					.map(card -> new CodefHeldCardMatcher.ExistingCard(card.getUserCardId(), card.getCardName(),
							card.getIssueDate()))
					.toList();
		List<CodefHeldCardCandidate> cards = usable.items();
		List<CodefHeldCardMatcher.Resolution> resolutions = CodefHeldCardMatcher.match(cards, existing);
		List<CodefSyncResult.NewCandidate> created = new ArrayList<>();
		Map<String, Long> userCardIds = new HashMap<>();
		for (int index = 0; index < cards.size(); index++) {
			Long userCardId = switch (resolutions.get(index)) {
				case CodefHeldCardMatcher.Resolution.Create create -> {
					RegisteredCodefCard registered = this.userCardService.registerCodefCard(user,
							create.displayName(), CodefCardNameDictionary.candidateFor(create.cardName()).orElse(null));
					long id = registered.userCardId();
					this.heldCards.save(new CodefHeldCardEntity(id, user.userId(), create.cardName(),
							create.issueDate(), syncId));
					created.add(new CodefSyncResult.NewCandidate(id, create.cardName(),
							registered.candidateCardProductId()));
					yield id;
				}
				case CodefHeldCardMatcher.Resolution.Existing same -> {
					// 재동기화: 후보 없는 UNMAPPED 카드만 다시 대조한다. 적용 조건은 보유 카드 서비스가 확인한다(#268)
					CodefCardNameDictionary.candidateFor(cards.get(index).cardName().orElseThrow())
						.ifPresent(product -> this.userCardService.proposeCodefCandidate(user, same.userCardId(),
								product));
					yield same.userCardId();
				}
				case CodefHeldCardMatcher.Resolution.Skipped skipped -> null;
			};
			// 카드 연결기(R1)는 응답에 이름이 하나뿐인 카드만 연결하므로 이름으로 찾아도 하나로 정해진다
			if (userCardId != null) {
				userCardIds.put(cards.get(index).cardName().orElseThrow(), userCardId);
			}
		}
		return new HeldCardStep(new CodefSyncResult.HeldCards(cards.size(), created), userCardIds);
	}

	private CodefSyncResult.Approvals storeApprovals(CurrentUser user, long syncId, LocalDateTime syncedAt,
			List<CodefApproval> approvals, CodefCardLinker linker, Map<String, Long> userCardIds,
			CodefApprovalFingerprinter fingerprinter) {
		Map<CodefApprovalKey, CodefApprovalRowEntity> recorded = new HashMap<>();
		for (CodefApprovalRowEntity row : this.approvalRows.findByUserId(user.userId())) {
			recorded.put(keyOf(row), row);
		}
		Map<CodefApprovalKey, CodefLedgerRowKind> previous = new HashMap<>();
		recorded.forEach((key, row) -> previous.put(key, row.getRowKind()));
		CodefLedgerCandidates candidates = CodefLedgerCandidateBuilder.build(CodefConnectionView.KB_KOOKMIN_CARD,
				linker, approvals, fingerprinter, previous);
		int imported = 0;
		int skipped = 0;
		List<CodefSyncResult.IncompleteRow> incomplete = new ArrayList<>();
		for (CodefLedgerCandidateRow row : candidates.rows()) {
			Long userCardId = userCardIdOf(row.card(), userCardIds);
			boolean approvalNoPresent = approvals.get(row.position()).approvalNo().isPresent();
			switch (row.decision()) {
				case CodefLedgerCandidateRow.Decision.Incomplete incompleteRow ->
					incomplete.add(new CodefSyncResult.IncompleteRow(userCardId, approvalNoPresent,
							incompleteRow.reason()));
				case CodefLedgerCandidateRow.Decision.Accepted accepted when userCardId == null ->
					// 건너뛴 CODEF 카드(Q2)의 행은 붙일 보유 카드가 없어 카드 미연결과 같게 둔다
					incomplete.add(new CodefSyncResult.IncompleteRow(null, approvalNoPresent,
							CodefIncompleteReason.UNDETERMINED_ROW));
				case CodefLedgerCandidateRow.Decision.Accepted accepted -> {
					switch (accepted.recheck()) {
						case NEW -> {
							CodefApprovalKey key = accepted.key();
							this.approvalRows.save(new CodefApprovalRowEntity(user.userId(), userCardId, syncId,
									key.cardName(), key.approval().value(), key.usedDate(), key.usedTime(),
									key.signedAmount(), accepted.kind(), syncedAt));
							imported++;
						}
						case ALREADY_RECORDED -> skipped++;
						case CANCELED_SINCE_RECORDED -> {
							recorded.get(accepted.key()).markFullyCanceled(syncedAt);
							imported++;
						}
					}
				}
			}
		}
		return new CodefSyncResult.Approvals(imported, skipped, incomplete);
	}

	private static CodefApprovalKey keyOf(CodefApprovalRowEntity row) {
		return new CodefApprovalKey(row.getCardName(), new CodefApprovalFingerprint(row.getApprovalFingerprint()),
				row.getUsedDate(), row.getUsedTime(), row.getSignedAmount());
	}

	private List<CodefSyncResult.PerformanceItem> storePerformances(CurrentUser user, long syncId,
			LocalDateTime syncedAt, CodefTargetOutcome<CodefPerformanceReference> outcome, CodefCardLinker linker,
			Map<String, Long> userCardIds) {
		if (!(outcome instanceof CodefTargetOutcome.Usable<CodefPerformanceReference> usable)) {
			return List.of();
		}
		List<CodefSyncResult.PerformanceItem> items = new ArrayList<>();
		for (CodefPerformanceLink link : linker.linkPerformances(usable.items())) {
			Long userCardId = userCardIdOf(link.card(), userCardIds);
			String cardName = link.reference().cardName().orElse(null);
			for (CodefSyncResult.PerformanceItem item : flatten(userCardId, link.reference(), syncedAt)) {
				this.performanceReferences.save(new CodefPerformanceReferenceEntity(user.userId(), userCardId, syncId,
						cardName, item.periodFrom(), item.periodTo(), item.currentUseWon(), syncedAt));
				items.add(item);
			}
		}
		return items;
	}

	/**
	 * 카드 한 장의 실적을 실적현황 하나당 항목 하나로 편다(관리자 판단 Q5). 혜택·실적현황 목록이 누락·형식 오류면 값을 모르는 항목 하나를
	 * 남겨 0건과 구분하고, 빈 목록이면 항목을 만들지 않는다.
	 */
	static List<CodefSyncResult.PerformanceItem> flatten(Long userCardId, CodefPerformanceReference reference,
			LocalDateTime fetchedAt) {
		CodefSyncResult.PerformanceItem unknown = new CodefSyncResult.PerformanceItem(userCardId, null, null, null,
				fetchedAt);
		if (!(reference.benefits() instanceof CodefList.Items<CodefPerformanceReference.BenefitReference> benefits)) {
			return List.of(unknown);
		}
		List<CodefSyncResult.PerformanceItem> items = new ArrayList<>();
		for (CodefPerformanceReference.BenefitReference benefit : benefits.items()) {
			if (!(benefit
				.performances() instanceof CodefList.Items<CodefPerformanceReference.PerformanceEntry> entries)) {
				items.add(unknown);
				continue;
			}
			for (CodefPerformanceReference.PerformanceEntry entry : entries.items()) {
				items.add(new CodefSyncResult.PerformanceItem(userCardId, entry.startDate().value(),
						entry.endDate().value(), entry.currentUse().value(), fetchedAt));
			}
		}
		return items;
	}

	private static Long userCardIdOf(CodefCardLink card, Map<String, Long> userCardIds) {
		return (card instanceof CodefCardLink.Linked linked) ? userCardIds.get(linked.cardName()) : null;
	}

	private record Connection(String organization, String ciphertext, CodefConnectionStatus status) {

		@Override
		public String toString() {
			return "Connection[organization=" + this.organization + ", status=" + this.status + "]";
		}

	}

	private record Fetched(CodefCallResult<CodefHeldCard> heldCards, CodefCallResult<CodefApproval> approvals,
			CodefCallResult<CodefCardPerformance> performances) {
	}

	private record HeldCardStep(CodefSyncResult.HeldCards result, Map<String, Long> userCardIds) {
	}

}
