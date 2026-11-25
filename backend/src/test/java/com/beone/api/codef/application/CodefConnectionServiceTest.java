package com.beone.api.codef.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.beone.api.codef.persistence.CodefConnectionEntity;
import com.beone.api.codef.persistence.CodefConnectionRepository;
import com.beone.api.user.application.CurrentUser;
import com.beone.api.user.application.DemoUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CODEF 연결 조회·등록 규칙(docs/api/codef.md, 관리자 판단 P1·P3·P7). 기대값은 계약(최초 201·재연결 200, 응답에 connectedId 없음,
 * 연결 없으면 NOT_CONNECTED)과 관리자 판단(성공 시 교체·실패 시 기존 유지, 비밀번호 보관 안 함)에서 썼다.
 */
class CodefConnectionServiceTest {

	private static final CurrentUser ME = new CurrentUser(1L, DemoUser.MY_CARDS);

	private static final CurrentUser PORTFOLIO = new CurrentUser(2L, DemoUser.VIRTUAL_PORTFOLIO);

	// 2026-11-04 09:00:00 서울
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T00:00:00.400Z"), ZoneOffset.UTC);

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 9, 0, 0);

	private final CodefConnectionRepository repository = mock(CodefConnectionRepository.class);

	private final CodefGateway gateway = mock(CodefGateway.class);

	private static final byte[] KEY = new byte[32];

	private static final ConnectedIdCipher CIPHER = new ConnectedIdCipher(KEY);

	private final CodefConnectionService service = service(this.gateway, true);

	@Test
	void notConnectedUserSeesNotConnected() {
		given(this.repository.findById(1L)).willReturn(Optional.empty());

		assertThat(this.service.get(ME)).isEqualTo(CodefConnectionView.NOT_CONNECTED);
		assertThat(CodefConnectionView.NOT_CONNECTED.organization()).isEqualTo("0301");
		assertThat(CodefConnectionView.NOT_CONNECTED.reconnectRequired()).isFalse();
	}

	@Test
	void firstConnectionStoresTheConnectedIdAndIsCreated() {
		char[] password = "secret".toCharArray();
		given(this.gateway.register("0301", "kbuser01", password))
			.willReturn(new CodefRegistration.Registered("cid-new"));
		given(this.repository.lockByUserId(1L)).willReturn(Optional.empty());
		ArgumentCaptor<CodefConnectionEntity> saved = ArgumentCaptor.forClass(CodefConnectionEntity.class);
		given(this.repository.save(saved.capture())).willAnswer(invocation -> invocation.getArgument(0));

		CodefConnectResult result = this.service.connect(ME, "kbuser01", password);

		String stored = saved.getValue().getConnectedIdCiphertext();
		assertThat(stored).startsWith("v1:").doesNotContain("cid-new");
		assertThat(CIPHER.decrypt(1L, stored)).isEqualTo("cid-new");
		assertThat(saved.getValue().toString()).doesNotContain(stored);
		assertThat(result).isEqualTo(new CodefConnectResult(
				new CodefConnectionView("0301", CodefConnectionStatus.CONNECTED, NOW, null), true));
		assertThat(password).containsOnly('\0');
		InOrder order = inOrder(this.gateway, this.repository);
		order.verify(this.gateway).register("0301", "kbuser01", password);
		order.verify(this.repository).lockUser(1L);
		order.verify(this.repository).lockByUserId(1L);
		verify(this.gateway, never()).release(anyString());
	}

	@Test
	void reconnectionReplacesTheConnectedIdKeepsLastSyncAndReleasesTheOldOneAfterward() {
		CodefConnectionEntity existing = new CodefConnectionEntity(1L, "0301", CIPHER.encrypt(1L, "cid-old"),
				LocalDateTime.of(2026, 11, 1, 8, 0));
		ReflectionTestUtils.setField(existing, "lastSyncedAt",
				LocalDateTime.of(2026, 11, 2, 8, 0));
		ReflectionTestUtils.setField(existing, "status",
				CodefConnectionStatus.RECONNECT_REQUIRED);
		given(this.gateway.register(anyString(), anyString(), any()))
			.willReturn(new CodefRegistration.Registered("cid-new"));
		given(this.repository.lockByUserId(1L)).willReturn(Optional.of(existing));

		CodefConnectResult result = this.service.connect(ME, "kbuser01", "pw".toCharArray());

		assertThat(result).isEqualTo(new CodefConnectResult(new CodefConnectionView("0301",
				CodefConnectionStatus.CONNECTED, NOW, LocalDateTime.of(2026, 11, 2, 8, 0)), false));
		assertThat(CIPHER.decrypt(1L, existing.getConnectedIdCiphertext())).isEqualTo("cid-new");
		verify(this.gateway).release("cid-old");
		verify(this.repository, never()).save(any());
	}

	@Test
	void failedRegistrationsMapToTheirExceptionsAndLeaveStorageUntouched() {
		assertFailure(new CodefRegistration.LoginRejected(), CodefLoginFailedException.class);
		assertFailure(new CodefRegistration.CardVerificationRequired(),
				CodefCardVerificationNotSupportedException.class);
		assertFailure(new CodefRegistration.Unavailable(), CodefUnavailableException.class);
		verifyNoInteractions(this.repository);
		verify(this.gateway, never()).release(anyString());
	}

	@Test
	void portfolioUserIsRejectedBeforeCallingCodef() {
		char[] password = "pw".toCharArray();

		assertThatThrownBy(() -> this.service.connect(PORTFOLIO, "kbuser01", password))
			.isInstanceOf(CodefPortfolioReadOnlyException.class);
		assertThat(password).containsOnly('\0');
		verifyNoInteractions(this.gateway, this.repository);
	}

	@Test
	void withoutAGatewayConnectionIsUnavailable() {
		char[] password = "pw".toCharArray();

		assertThatThrownBy(() -> service(null, true).connect(ME, "kbuser01", password))
			.isInstanceOf(CodefUnavailableException.class);
		assertThat(password).containsOnly('\0');
		verifyNoInteractions(this.repository);
	}

	@Test
	void withoutAnEncryptionKeyConnectionFailsBeforeCallingCodefAndStoresNothing() {
		char[] password = "pw".toCharArray();

		assertThatThrownBy(() -> service(this.gateway, false).connect(ME, "kbuser01", password))
			.isInstanceOf(CodefUnavailableException.class);
		assertThat(password).containsOnly('\0');
		verifyNoInteractions(this.gateway, this.repository);
	}

	@Test
	void disconnectDeletesTheRowAndReleasesTheDecryptedConnectedIdAfterward() {
		CodefConnectionEntity existing = new CodefConnectionEntity(1L, "0301", CIPHER.encrypt(1L, "cid-old"), NOW);
		given(this.repository.lockByUserId(1L)).willReturn(Optional.of(existing));

		this.service.disconnect(ME);

		InOrder order = inOrder(this.repository, this.gateway);
		order.verify(this.repository).lockUser(1L);
		order.verify(this.repository).lockByUserId(1L);
		order.verify(this.repository).delete(existing);
		order.verify(this.gateway).release("cid-old");
	}

	@Test
	void disconnectWithoutAConnectionIsNotConnected() {
		given(this.repository.lockByUserId(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.disconnect(ME)).isInstanceOf(CodefNotConnectedException.class);
		verify(this.repository, never()).delete(any());
		verifyNoInteractions(this.gateway);
	}

	@Test
	void portfolioUserCannotDisconnect() {
		assertThatThrownBy(() -> this.service.disconnect(PORTFOLIO)).isInstanceOf(CodefPortfolioReadOnlyException.class);
		verifyNoInteractions(this.repository, this.gateway);
	}

	@Test
	void disconnectWithoutAKeyStillDeletesButSkipsRelease() {
		CodefConnectionEntity existing = new CodefConnectionEntity(1L, "0301", CIPHER.encrypt(1L, "cid-old"), NOW);
		given(this.repository.lockByUserId(1L)).willReturn(Optional.of(existing));

		service(this.gateway, false).disconnect(ME);

		verify(this.repository).delete(existing);
		verifyNoInteractions(this.gateway);
	}

	@Test
	void storageFailureReleasesTheJustIssuedConnectedId() {
		given(this.gateway.register(anyString(), anyString(), any()))
			.willReturn(new CodefRegistration.Registered("cid-new"));
		willThrow(new IllegalStateException("db")).given(this.repository).lockUser(1L);

		assertThatThrownBy(() -> this.service.connect(ME, "kbuser01", "pw".toCharArray()))
			.isInstanceOf(IllegalStateException.class);
		verify(this.gateway).release("cid-new");
	}

	@Test
	void releaseFailureDoesNotUndoTheReconnection() {
		CodefConnectionEntity existing = new CodefConnectionEntity(1L, "0301", CIPHER.encrypt(1L, "cid-old"),
				NOW.minusDays(1));
		given(this.gateway.register(anyString(), anyString(), any()))
			.willReturn(new CodefRegistration.Registered("cid-new"));
		given(this.repository.lockByUserId(1L)).willReturn(Optional.of(existing));
		willThrow(new IllegalStateException("codef")).given(this.gateway).release("cid-old");

		assertThat(this.service.connect(ME, "kbuser01", "pw".toCharArray()).connection().status())
			.isEqualTo(CodefConnectionStatus.CONNECTED);
		assertThat(CIPHER.decrypt(1L, existing.getConnectedIdCiphertext())).isEqualTo("cid-new");
	}

	private void assertFailure(CodefRegistration registration, Class<? extends RuntimeException> expected) {
		char[] password = "pw".toCharArray();
		given(this.gateway.register(anyString(), anyString(), any())).willReturn(registration);

		assertThatThrownBy(() -> this.service.connect(ME, "kbuser01", password)).isInstanceOf(expected);
		assertThat(password).containsOnly('\0');
	}

	private CodefConnectionService service(CodefGateway gateway, boolean withKey) {
		StaticListableBeanFactory beans = new StaticListableBeanFactory();
		if (gateway != null) {
			beans.addBean("gateway", gateway);
		}
		ObjectProvider<CodefGateway> provider = beans.getBeanProvider(CodefGateway.class);
		ConnectedIdKeySource keys = () -> withKey ? Optional.of(KEY.clone()) : Optional.empty();
		return new CodefConnectionService(this.repository, provider, keys,
				new TransactionTemplate(new NoOpTransactions()), CLOCK);
	}

	// 저장소를 대역으로 두므로 실제 트랜잭션 없이 콜백만 실행한다
	private static final class NoOpTransactions extends AbstractPlatformTransactionManager {

		private static final long serialVersionUID = 1L;

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
		}

	}

}
