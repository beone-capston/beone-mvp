package com.beone.api.card.web;

import com.beone.api.card.application.UserCardService;
import com.beone.api.common.web.ApiResponse;
import com.beone.api.user.application.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보유 카드 API 5개(docs/api/cards.md). 현재 사용자는 {@link CurrentUser} 인자로만 받는다(D-09).
 *
 * <p>{@code local}·{@code demo}와 로컬 회원 프로필 {@code member}에서만 등록한다. 그 밖의 환경에서는 경로가 없어 404 {@code NOT_FOUND}다.
 */
@RestController
@Profile({ "local", "demo", "member" })
@RequestMapping("/api/v1/me/cards")
public class UserCardController {

	private final UserCardService userCardService;

	public UserCardController(UserCardService userCardService) {
		this.userCardService = userCardService;
	}

	@GetMapping
	public ApiResponse<UserCardResponses.ItemList> listCards(CurrentUser currentUser) {
		return ApiResponse.of(UserCardResponses.ItemList.from(this.userCardService.list(currentUser)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<UserCardResponses.Item> registerCard(CurrentUser currentUser,
			@Valid @RequestBody RegisterUserCardRequest request) {
		return ApiResponse
			.of(UserCardResponses.Item.from(this.userCardService.register(currentUser, request.toNewUserCard())));
	}

	@PatchMapping("/{userCardId}/mapping")
	public ApiResponse<UserCardResponses.MappingChanged> changeMapping(CurrentUser currentUser,
			@PathVariable String userCardId, @Valid @RequestBody ChangeMappingRequest request) {
		return ApiResponse.of(UserCardResponses.MappingChanged
			.from(this.userCardService.changeMapping(currentUser, userCardId, request.toMappingChange())));
	}

	@PatchMapping("/{userCardId}")
	public ApiResponse<UserCardResponses.Item> updateCard(CurrentUser currentUser, @PathVariable String userCardId,
			@Valid @RequestBody UpdateUserCardRequest request) {
		return ApiResponse.of(UserCardResponses.Item
			.from(this.userCardService.update(currentUser, userCardId, request.toUserCardChange())));
	}

	@DeleteMapping("/{userCardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void releaseCard(CurrentUser currentUser, @PathVariable String userCardId) {
		this.userCardService.release(currentUser, userCardId);
	}

}
