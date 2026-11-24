package com.beone.api.user.application;

import com.beone.api.user.persistence.UserEntity;
import com.beone.api.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 사용자(장부)의 표시 정보를 DB 행에서 읽는다. 시연 사용자와 회원 장부를 같은 방식으로 다룬다.
 *
 * <p>행이 없으면 현재 사용자 결정 지점이 잘못 만든 값이므로 {@link IllegalStateException}을 던진다(공통 처리기에서 500).
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService {

	private final UserRepository userRepository;

	public UserProfileService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * 현재 사용자의 표시 정보.
	 * @param user 현재 사용자
	 * @return 표시 정보
	 */
	public UserProfile profileOf(CurrentUser user) {
		UserEntity entity = this.userRepository.findById(user.userId())
			.orElseThrow(() -> new IllegalStateException("현재 사용자 행이 없습니다."));
		return new UserProfile(entity.getDemoUser(), entity.getDisplayName(), entity.getPortfolioType(),
				entity.isReadOnly());
	}

}
