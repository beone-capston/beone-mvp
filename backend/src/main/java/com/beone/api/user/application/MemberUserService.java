package com.beone.api.user.application;

import java.util.Optional;

import com.beone.api.auth.application.MemberEmailVerifiedEvent;
import com.beone.api.auth.application.MemberWithdrawnEvent;
import com.beone.api.user.persistence.MemberLedgerEraser;
import com.beone.api.user.persistence.UserEntity;
import com.beone.api.user.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원과 업무 장부를 잇는다(결정 37 D1a). 회원 1명은 '내 카드'(REAL) 장부 1개를 가진다.
 *
 * <p>장부는 이메일 인증 완료와 같은 트랜잭션에서 만든다(관리자 판단, 결정 28 범위). 이미 인증된 회원은 Flyway {@code V14}가 만들었다.
 * auth는 이 패키지를 알지 않고 이벤트만 발행한다(user → auth 한 방향 의존).
 */
@Service
public class MemberUserService {

	private static final Logger log = LoggerFactory.getLogger(MemberUserService.class);

	private final UserRepository userRepository;

	private final MemberLedgerEraser ledgerEraser;

	private final ApplicationEventPublisher events;

	public MemberUserService(UserRepository userRepository, MemberLedgerEraser ledgerEraser,
			ApplicationEventPublisher events) {
		this.userRepository = userRepository;
		this.ledgerEraser = ledgerEraser;
		this.events = events;
	}

	/**
	 * 회원의 장부를 현재 사용자로 돌려준다.
	 * @param memberId 회원 ID
	 * @return 현재 사용자, 장부가 없으면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<CurrentUser> currentUserOf(String memberId) {
		return this.userRepository.findByMemberId(memberId)
			.map(user -> new CurrentUser(user.getUserId(), null, user.getPortfolioType(), user.isReadOnly()));
	}

	/**
	 * 이메일 인증을 마친 회원의 장부를 만든다. 이미 있으면 아무것도 하지 않는다. 발행한 인증 트랜잭션 안에서 실행하므로 장부를 만들지
	 * 못하면 인증도 함께 취소된다.
	 * @param event 이메일 인증 완료
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onEmailVerified(MemberEmailVerifiedEvent event) {
		if (!this.userRepository.existsByMemberId(event.memberId())) {
			this.userRepository.save(UserEntity.memberCards(event.memberId()));
		}
	}

	/**
	 * 탈퇴하는 회원의 장부와 그 업무 행을 지운다(결정 D10). 지우기 전에 {@link MemberLedgerErasingEvent}를 발행해 업무 패키지가 지워질 값을
	 * 읽게 한다. 발행한 탈퇴 트랜잭션 안에서 실행하므로 하나라도 실패하면 탈퇴 전체가 취소된다. 장부가 없으면 아무것도 하지 않는다.
	 * @param event 회원 탈퇴
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void onWithdrawn(MemberWithdrawnEvent event) {
		Optional<UserEntity> ledger = this.userRepository.findByMemberId(event.memberId());
		if (ledger.isEmpty()) {
			return;
		}
		long userId = ledger.get().getUserId();
		this.events.publishEvent(new MemberLedgerErasingEvent(userId, event.memberRef()));
		this.userRepository.flush();
		int rows = this.ledgerEraser.erase(userId);
		log.info("member ledger erased ref={} rows={}", event.memberRef(), rows);
	}

}
