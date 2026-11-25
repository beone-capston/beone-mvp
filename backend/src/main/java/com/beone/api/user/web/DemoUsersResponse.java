package com.beone.api.user.web;

import java.util.List;

import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.DemoUserProfile;
import com.beone.api.user.application.PortfolioType;

/**
 * {@code GET /api/v1/demo-users}의 {@code data}.
 *
 * @param users 시연 사용자 목록
 */
public record DemoUsersResponse(List<Item> users) {

	public DemoUsersResponse {
		users = List.copyOf(users);
	}

	static DemoUsersResponse from(List<DemoUserProfile> profiles) {
		return new DemoUsersResponse(profiles.stream().map(Item::from).toList());
	}

	/**
	 * 시연 사용자 한 명.
	 *
	 * @param demoUser {@code X-BeONE-Demo-User}에 넣을 값
	 * @param displayName 화면 표시 이름
	 * @param portfolioType {@code REAL}·{@code SYNTHETIC}
	 * @param readOnly 보유 카드·CODEF 쓰기 불가 여부(D-19)
	 */
	public record Item(DemoUser demoUser, String displayName, PortfolioType portfolioType, boolean readOnly) {

		static Item from(DemoUserProfile profile) {
			return new Item(profile.demoUser(), profile.displayName(), profile.portfolioType(), profile.readOnly());
		}

	}

}
