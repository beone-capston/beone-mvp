package com.beone.api.order.application;

import java.time.YearMonth;
import java.util.List;

import com.beone.api.order.persistence.PlannedSpendEntity;
import com.beone.api.order.persistence.PlannedSpendRepository;
import com.beone.api.user.application.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이번 달 예정 소비를 저장·조회한다. 1차에서는 저장만 하고 추천 계산에 쓰지 않는다(D-18 K4).
 *
 * <p>"이번 달"은 {@link OrderClock}의 서울 기준 연-월이다. 조회는 이번 달 항목만, 교체는 사용자의 기존 항목 전체를 지운 뒤
 * 요청 순서대로 새로 저장한다(결정 27).
 */
@Service
public class PlannedSpendService {

	/** 한 사용자가 둘 수 있는 예정 소비 최대 건수(docs/api/orders.md). */
	public static final int MAX_ITEMS = 5;

	private final PlannedSpendRepository plannedSpendRepository;

	private final OrderClock orderClock;

	public PlannedSpendService(PlannedSpendRepository plannedSpendRepository, OrderClock orderClock) {
		this.plannedSpendRepository = plannedSpendRepository;
		this.orderClock = orderClock;
	}

	/**
	 * 현재 사용자의 이번 달 예정 소비를 요청 순서대로 돌려준다.
	 * @param user 현재 사용자
	 * @return 이번 달 예정 소비 목록
	 */
	@Transactional(readOnly = true)
	public List<PlannedSpendDetail> list(CurrentUser user) {
		return findCurrentMonth(user, this.orderClock.currentMonth());
	}

	/**
	 * 현재 사용자의 예정 소비를 통째로 교체한다. 빈 목록이면 모두 지운다.
	 * @param user 현재 사용자
	 * @param items 교체할 예정 소비 목록
	 * @return 저장한 예정 소비 목록
	 * @throws PlannedDateOutOfMonthException 이번 달 밖 날짜가 있을 때. 웹 검증 뒤 달이 바뀐 경우이며 웹 처리기가 400으로 바꾼다
	 * @throws IllegalArgumentException 5건을 넘을 때. 웹 경계에서 먼저 400으로 걸러지므로 여기서는 불변식 위반이다
	 */
	@Transactional
	public List<PlannedSpendDetail> replace(CurrentUser user, List<NewPlannedSpend> items) {
		YearMonth month = this.orderClock.currentMonth();
		if (items.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("too many planned spends");
		}
		if (items.stream().anyMatch(item -> !YearMonth.from(item.plannedDate()).equals(month))) {
			throw new PlannedDateOutOfMonthException();
		}
		this.plannedSpendRepository.deleteAllOfUser(user.userId());
		this.plannedSpendRepository.saveAllAndFlush(items.stream()
			.map(item -> new PlannedSpendEntity(user.userId(), item.plannedDate(), item.plannedTime(),
					item.amountWon(), item.merchantClass(), item.paymentMethod(), item.merchantName()))
			.toList());
		return findCurrentMonth(user, month);
	}

	private List<PlannedSpendDetail> findCurrentMonth(CurrentUser user, YearMonth month) {
		return this.plannedSpendRepository
			.findByUserIdAndPlannedDateBetweenOrderByPlannedSpendIdAsc(user.userId(), month.atDay(1),
					month.atEndOfMonth())
			.stream()
			.map(PlannedSpendService::toDetail)
			.toList();
	}

	private static PlannedSpendDetail toDetail(PlannedSpendEntity entity) {
		return new PlannedSpendDetail(PlannedSpendId.format(entity.getPlannedSpendId()), entity.getPlannedDate(),
				entity.getAmountWon(), entity.getMerchantClass(), entity.getPaymentMethod(), entity.getMerchantName(),
				entity.getPlannedTime());
	}

}
