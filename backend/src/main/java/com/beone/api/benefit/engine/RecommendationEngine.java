package com.beone.api.benefit.engine;

/**
 * 주문 하나에 대해 보유 카드별 혜택을 계산하고 추천 카드를 정하는 입구(D-22).
 *
 * <p>같은 입력과 규칙 버전이면 결과가 같다. 계산 상태({@code 확인 필요}, 미충족, 범위 밖)는 예외가 아니라 결과에 담는다.
 *
 * <p>값의 책임은 겹치지 않는다.
 * <ul>
 * <li>기준 기간: 엔진이 {@link StateInput#periodFrom()}·{@link StateInput#periodTo()}를 주문 기준 기간과 비교해
 * 맞지 않으면 {@code 확인 필요}로 처리한다. 업무 API는 기간을 판단하지 않는다.</li>
 * <li>출처 충돌 등 업무 API만 아는 사유: 업무 API가 {@link StateInput#usable()}·{@link StateInput#reason()}으로 전한다.</li>
 * <li>사용액 유효값: 업무 API가 스냅샷과 스냅샷 뒤 승인의 {@link BucketUsageEffect}를 합쳐 넘긴다(D-16).</li>
 * </ul>
 */
public interface RecommendationEngine {

	/**
	 * 추천을 계산한다.
	 *
	 * @param request 생성 시 계약을 검증한 입력
	 * @return 업무 API가 응답을 만들 수 있는 결과
	 */
	EngineResult recommend(EngineRequest request);

}
