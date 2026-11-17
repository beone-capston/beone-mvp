package com.beone.api.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import com.beone.api.payment.persistence.IdempotencyKeyEntity;
import com.beone.api.payment.persistence.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 멱등 키 규칙(docs/api.md 중복 방지): 24시간 보존, 같은 지문은 같은 결과, 다른 지문은 409, 사용자별.
 */
class IdempotencyStoreTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0, 0);

	private final IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);

	private final IdempotencyStore store = new IdempotencyStore(this.repository);

	@Test
	void unknownKeyHasNothingToReplay() {
		given(this.repository.findByUserIdAndIdempotencyKey(1L, "k")).willReturn(Optional.empty());

		assertThat(this.store.replay(1L, "k", "fp", NOW)).isEmpty();
	}

	@Test
	void sameFingerprintWithinRetentionReplaysTheStoredResponse() {
		given(this.repository.findByUserIdAndIdempotencyKey(1L, "k"))
			.willReturn(Optional.of(new IdempotencyKeyEntity(1L, "k", "fp", "{\"x\":1}", NOW.minusHours(23))));

		assertThat(this.store.replay(1L, "k", "fp", NOW)).contains("{\"x\":1}");
	}

	@Test
	void differentFingerprintIsRejected() {
		given(this.repository.findByUserIdAndIdempotencyKey(1L, "k"))
			.willReturn(Optional.of(new IdempotencyKeyEntity(1L, "k", "fp", "{}", NOW.minusMinutes(1))));

		assertThatThrownBy(() -> this.store.replay(1L, "k", "other", NOW))
			.isInstanceOf(IdempotencyKeyReusedException.class);
	}

	@Test
	void keyOlderThan24HoursIsForgottenEvenWithAnotherFingerprint() {
		IdempotencyKeyEntity expired = new IdempotencyKeyEntity(1L, "k", "fp", "{}", NOW.minusHours(24));
		given(this.repository.findByUserIdAndIdempotencyKey(1L, "k")).willReturn(Optional.of(expired));

		assertThat(this.store.replay(1L, "k", "other", NOW)).isEmpty();
		verify(this.repository).delete(expired);
	}

	@Test
	void concurrentSaveOfTheSameKeyIsReportedAsReuse() {
		given(this.repository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> this.store.remember(1L, "k", "fp", "{}", NOW))
			.isInstanceOf(IdempotencyKeyReusedException.class);
	}

}
