package com.beone.api.user.application;

import java.util.Comparator;
import java.util.List;

import com.beone.api.user.persistence.UserEntity;
import com.beone.api.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드로 넣은 시연 사용자를 조회한다(D-09).
 *
 * <p>시드 행이 없으면 설정 오류이므로 {@link IllegalStateException}을 던진다(공통 처리기에서 500).
 */
@Service
@Transactional(readOnly = true)
public class DemoUserService {

	private final UserRepository userRepository;

	public DemoUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** 시연 사용자 목록을 {@link DemoUser} 선언 순서로 돌려준다. 회원 장부는 넣지 않는다. */
	public List<DemoUserProfile> listDemoUsers() {
		return this.userRepository.findByDemoUserIsNotNull()
			.stream()
			.sorted(Comparator.comparing(UserEntity::getDemoUser))
			.map(DemoUserService::toProfile)
			.toList();
	}

	/** 시연 사용자 구분에 해당하는 현재 사용자를 돌려준다. */
	public CurrentUser currentUserOf(DemoUser demoUser) {
		UserEntity user = this.userRepository.findByDemoUser(demoUser)
			.orElseThrow(() -> new IllegalStateException("시연 사용자 시드가 없습니다."));
		return new CurrentUser(user.getUserId(), user.getDemoUser(), user.getPortfolioType(), user.isReadOnly());
	}

	private static DemoUserProfile toProfile(UserEntity user) {
		return new DemoUserProfile(user.getDemoUser(), user.getDisplayName(), user.getPortfolioType(),
				user.isReadOnly());
	}

}
