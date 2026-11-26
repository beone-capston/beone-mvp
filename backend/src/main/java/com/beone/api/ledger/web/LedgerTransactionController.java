package com.beone.api.ledger.web;

import com.beone.api.common.web.ApiResponse;
import com.beone.api.ledger.application.LedgerMonthView;
import com.beone.api.ledger.application.LedgerQueryService;
import com.beone.api.user.application.CurrentUser;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 원장 조회 API(docs/api/ledger.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo}·{@code member} 프로필에서 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 * {@code member}에서는 회원 세션의 장부가 현재 사용자이고, 세션 401·캐시 금지는 {@code /api/v1/me/**} 공통 인터셉터·필터가
 * 처리한다(결정 37). 원장 행은 모의 결제 API가 쓰고 이 API는 조회만 한다. member에는 CODEF 경로가 없다.
 * {@code month}가 없으면 공통 처리기가 400 필수 값 오류를 만든다. 페이지 나눔은 계약이 정하지 않아 없다.
 */
@RestController
@Profile({ "local", "demo", "member" })
@RequestMapping("/api/v1/me/ledger-transactions")
public class LedgerTransactionController {

	private final LedgerQueryService ledgerQueryService;

	public LedgerTransactionController(LedgerQueryService ledgerQueryService) {
		this.ledgerQueryService = ledgerQueryService;
	}

	@GetMapping
	public ApiResponse<LedgerMonthView> getLedgerTransactions(CurrentUser currentUser, @RequestParam String month,
			@RequestParam(required = false) String userCardId, @RequestParam(required = false) String source) {
		return ApiResponse.of(this.ledgerQueryService.find(currentUser, LedgerQueryParameters.month(month), userCardId,
				LedgerQueryParameters.source(source)));
	}

}
