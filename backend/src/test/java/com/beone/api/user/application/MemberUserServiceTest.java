package com.beone.api.user.application;

import java.util.Optional;

import com.beone.api.auth.application.MemberEmailVerifiedEvent;
import com.beone.api.auth.application.MemberWithdrawnEvent;
import com.beone.api.user.persistence.MemberLedgerEraser;
import com.beone.api.user.persistence.UserEntity;
import com.beone.api.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 회원 장부 생성과 조회를 확인한다. 기대값은 결정 37 D1a·D7('내 카드'·REAL·쓰기 가능, 회원당 1개)이다.
 */
class MemberUserServiceTest {

	private UserRepository repository;

	private MemberLedgerEraser eraser;

	private ApplicationEventPublisher events;

	private MemberUserService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(UserRepository.class);
		this.eraser = mock(MemberLedgerEraser.class);
		this.events = mock(ApplicationEventPublisher.class);
		this.service = new MemberUserService(this.repository, this.eraser, this.events);
	}

	/** 결정 D10: 탈퇴는 장부 삭제 직전 이벤트(CODEF 해제 준비)를 먼저 발행한 뒤 장부를 지운다. */
	@Test
	void withdrawalAnnouncesThenErasesTheMemberLedger() {
		UserEntity ledger = UserEntity.memberCards("m-1");
		ReflectionTestUtils.setField(ledger, "userId", 42L);
		given(this.repository.findByMemberId("m-1")).willReturn(Optional.of(ledger));

		this.service.onWithdrawn(new MemberWithdrawnEvent("m-1", "abcdef012345"));

		InOrder order = inOrder(this.events, this.eraser);
		order.verify(this.events).publishEvent(new MemberLedgerErasingEvent(42L, "abcdef012345"));
		order.verify(this.eraser).erase(42L);
	}

	@Test
	void withdrawalWithoutLedgerErasesNothing() {
		given(this.repository.findByMemberId("m-1")).willReturn(Optional.empty());

		this.service.onWithdrawn(new MemberWithdrawnEvent("m-1", "abcdef012345"));

		verifyNoInteractions(this.events, this.eraser);
	}

	@Test
	void emailVerifiedCreatesMemberCardsLedgerOnce() {
		given(this.repository.existsByMemberId("m-1")).willReturn(false);

		this.service.onEmailVerified(new MemberEmailVerifiedEvent("m-1"));

		ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
		verify(this.repository).save(saved.capture());
		assertThat(saved.getValue().getMemberId()).isEqualTo("m-1");
		assertThat(saved.getValue().getDemoUser()).isNull();
		assertThat(saved.getValue().getDisplayName()).isEqualTo("내 카드");
		assertThat(saved.getValue().getPortfolioType()).isEqualTo(PortfolioType.REAL);
		assertThat(saved.getValue().isReadOnly()).isFalse();
	}

	@Test
	void existingLedgerIsNotCreatedAgain() {
		given(this.repository.existsByMemberId("m-1")).willReturn(true);

		this.service.onEmailVerified(new MemberEmailVerifiedEvent("m-1"));

		verify(this.repository, never()).save(any());
	}

	@Test
	void currentUserOfMemberHasNoDemoUser() {
		UserEntity entity = UserEntity.memberCards("m-1");
		ReflectionTestUtils.setField(entity, "userId", 3L);
		given(this.repository.findByMemberId("m-1")).willReturn(Optional.of(entity));
		given(this.repository.findByMemberId("m-2")).willReturn(Optional.empty());

		assertThat(this.service.currentUserOf("m-1")).get().satisfies(user -> {
			assertThat(user.userId()).isEqualTo(3L);
			assertThat(user.demoUser()).isNull();
			assertThat(user.portfolioType()).isEqualTo(PortfolioType.REAL);
			assertThat(user.readOnly()).isFalse();
		});
		assertThat(this.service.currentUserOf("m-2")).isEmpty();
	}

}
