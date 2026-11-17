package com.beone.api.ledger.application;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.beone.api.card.application.UserCardId;
import com.beone.api.card.application.UserCardService;
import com.beone.api.card.application.UserCardView;
import com.beone.api.ledger.persistence.LedgerTransactionEntity;
import com.beone.api.ledger.persistence.LedgerTransactionRepository;
import com.beone.api.ledger.persistence.LedgerUsageEffectRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장 조회(docs/api/ledger.md). 저장한 행을 그대로 보여 주며, 원장에서 전월 실적·버킷 사용액을 산출하지 않는다(D-16).
 *
 * <p>관리자 승인으로 정한 규칙(결정 28 보충, #118):
 * <ul>
 * <li>달 조건은 거래일({@code transactionDate})이 그 달인 행이다. 2차 CODEF 취소 행은 귀속월이 확인 필요라 귀속월로는 둘 곳이 없다.</li>
 * <li>정렬은 거래일 → 행 ID 오름차순이다.</li>
 * <li>대상 카드는 해제하지 않은 이 사용자의 보유 카드다. 해제한 카드의 행은 보이지 않는다.</li>
 * <li>완전성: 모의 승인·취소는 빠짐없이 기록하므로 {@code SIMULATION}은 완전하다. 1차는 CODEF 원장을 수집하지 않으므로
 * {@code CODEF}는 완전하다고 선언하지 않고({@code complete=false}) 사유는 비워 둔다. 사유는 2차 CODEF 연결 때 채운다.</li>
 * </ul>
 * 다른 패키지가 다시 쓸 수 있게 공개한다. 월간 상태 요약과 추천 입력은 {@link #activityAfter}로 스냅샷 뒤 활동을 읽는다(#120).
 */
@Service
public class LedgerQueryService {

	// 모의 결제 ID 접두사. payment가 ledger를 쓰므로 ledger가 payment를 참조하지 않게 여기 둔다(payment PaymentIds와 같은 값).
	private static final String SIMULATED_PAYMENT_PREFIX = "pay-";

	// docs/api.md 값 형식: 시각 없는 시간 값은 HH:mm:ss
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final LedgerTransactionRepository transactionRepository;

	private final LedgerUsageEffectRepository usageEffectRepository;

	private final UserCardService userCardService;

	public LedgerQueryService(LedgerTransactionRepository transactionRepository,
			LedgerUsageEffectRepository usageEffectRepository, UserCardService userCardService) {
		this.transactionRepository = transactionRepository;
		this.usageEffectRepository = usageEffectRepository;
		this.userCardService = userCardService;
	}

	/**
	 * 한 달의 원장 행과 카드·출처별 완전성을 찾는다.
	 * @param user 현재 사용자
	 * @param month 조회할 달
	 * @param userCardId 카드 하나로 좁힐 때 그 ID, 전체 카드면 {@code null}
	 * @param source 출처 하나로 좁힐 때 그 출처, 모든 출처면 {@code null}
	 * @return 조회 결과
	 * @throws LedgerCardNotFoundException {@code userCardId}가 없거나 다른 사용자·해제한 카드
	 */
	@Transactional(readOnly = true)
	public LedgerMonthView find(CurrentUser user, YearMonth month, String userCardId, LedgerSource source) {
		List<Long> cardIds = targetCards(user, userCardId);
		Set<Long> cardSet = Set.copyOf(cardIds);
		List<LedgerSource> sources = (source == null) ? List.of(LedgerSource.values()) : List.of(source);
		List<LedgerTransactionEntity> rows = this.transactionRepository
			.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAscLedgerIdAsc(user.userId(), month.atDay(1),
					month.atEndOfMonth())
			.stream()
			.filter(row -> cardSet.contains(row.getUserCardId()) && sources.contains(row.getSource()))
			.toList();
		return new LedgerMonthView(month.toString(), rows.stream().map(LedgerQueryService::toItem).toList(),
				coverage(cardIds, sources, month));
	}

	/**
	 * 한 카드의 월간 상태 스냅샷 뒤 활동(#120, D-16). 기록 시각이 {@code snapshotAt}보다 뒤인 행 중 승인은 자기 거래일이, 취소는
	 * 원 승인 행의 거래일이 그 달인 행을 행 ID 오름차순으로 돌려준다. 취소를 원 승인의 달에 귀속하므로 11월 승인을 12월에 취소하면
	 * 11월 활동에 들어가고, 10월 승인의 11월 취소는 11월 활동에 들어가지 않는다(D-17, 관리자 승인 규칙). 같은 초에 기록된 행은
	 * 스냅샷에 들어 있을 수 있으므로 넣지 않는다. 카드 소유 확인은 호출자가 한다.
	 * @param user 현재 사용자
	 * @param userCardId 보유 카드 ID. 형식이 틀리면 빈 목록
	 * @param month 월간 상태의 달
	 * @param snapshotAt 월간 상태 입력 시각(Asia/Seoul)
	 * @return 스냅샷 뒤 행. 취소 행에는 원 승인 행의 버킷 차감액을 붙인다
	 */
	@Transactional(readOnly = true)
	public List<LedgerActivity> activityAfter(CurrentUser user, String userCardId, YearMonth month,
			LocalDateTime snapshotAt) {
		Objects.requireNonNull(snapshotAt, "snapshotAt");
		return UserCardId.parse(userCardId)
			.map(cardId -> this.transactionRepository
				.findActivityAfter(user.userId(), cardId, month.atDay(1), month.atEndOfMonth(), snapshotAt,
						LedgerEntryType.APPROVAL, LedgerEntryType.CANCELLATION)
				.stream()
				.map(row -> new LedgerActivity(row.getEntryType(), row.getAmountWon(),
						effectsOf((row.getEntryType() == LedgerEntryType.CANCELLATION) ? row.getOriginalLedgerId()
								: row.getLedgerId())))
				.toList())
			.orElse(List.of());
	}

	private List<LedgerUsageEffect> effectsOf(Long ledgerId) {
		return this.usageEffectRepository.findByLedgerIdOrderByUsageEffectIdAsc(ledgerId)
			.stream()
			.map(effect -> new LedgerUsageEffect(effect.getLimitBucketId(), effect.getDeductionWon()))
			.toList();
	}

	private List<Long> targetCards(CurrentUser user, String userCardId) {
		Map<Long, UserCardView> held = this.userCardService.list(user)
			.stream()
			.collect(Collectors.toMap(card -> UserCardId.parse(card.userCardId()).orElseThrow(), Function.identity(),
					(first, second) -> first, LinkedHashMap::new));
		if (userCardId == null) {
			return List.copyOf(held.keySet());
		}
		Long id = UserCardId.parse(userCardId).filter(held::containsKey).orElseThrow(LedgerCardNotFoundException::new);
		return List.of(id);
	}

	private static List<LedgerMonthView.Coverage> coverage(List<Long> cardIds, List<LedgerSource> sources,
			YearMonth month) {
		LedgerMonthView.Period period = new LedgerMonthView.Period(month.atDay(1).toString(),
				month.atEndOfMonth().toString());
		List<LedgerMonthView.Coverage> coverage = new ArrayList<>();
		for (Long cardId : cardIds) {
			for (LedgerSource source : sources) {
				coverage.add(new LedgerMonthView.Coverage(UserCardId.format(cardId), source.name(), period,
						source == LedgerSource.SIMULATION, List.of()));
			}
		}
		return coverage;
	}

	private static LedgerMonthView.Item toItem(LedgerTransactionEntity row) {
		return new LedgerMonthView.Item(LedgerTransactionId.format(row.getLedgerId()),
				UserCardId.format(row.getUserCardId()), row.getSource().name(), row.getEntryType().name(),
				row.getAmountWon(), row.getTransactionDate().toString(),
				(row.getTransactionTime() == null) ? null : TIME.format(row.getTransactionTime()), row.getMerchantClass(),
				row.getPaymentMethod(), row.getTransactionNature(), row.getMerchantName(),
				(row.getOriginalLedgerId() == null) ? null : LedgerTransactionId.format(row.getOriginalLedgerId()),
				(row.getSimulatedPaymentId() == null) ? null
						: SIMULATED_PAYMENT_PREFIX + row.getSimulatedPaymentId(),
				new LedgerMonthView.AttributionMonth(row.getAttributionMonth(), row.getAttributionStatus().name()));
	}

}
