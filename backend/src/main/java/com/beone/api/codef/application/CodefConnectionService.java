package com.beone.api.codef.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import com.beone.api.codef.persistence.CodefConnectionEntity;
import com.beone.api.codef.persistence.CodefConnectionRepository;
import com.beone.api.user.application.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 현재 사용자의 CODEF 연결을 조회·등록·해제한다(docs/api/codef.md, D-09·D-14 원칙 9, 결정 30, 2026-11-18 사용자 직접 결정 P4·P5).
 *
 * <p>계정 등록은 {@link CodefGateway}를 부르는 동안 DB 트랜잭션을 열지 않는다. 등록이 성공한 뒤에만 짧은 트랜잭션으로 사용자 행을 잠그고
 * 연결 행을 만들거나 바꾼다. 재연결 성공이면 이전 connectedId를 새 값으로 바꾸고 커밋 뒤 정리하며, 실패하면 기존 연결을 그대로 둔다
 * (관리자 판단 P3). 저장이 실패하면 방금 받은 connectedId를 정리한다. 비밀번호는 게이트웨이에 넘긴 뒤 지우고 보관하지 않는다.
 *
 * <p>connectedId는 AES-256-GCM 암호문으로만 저장한다. 암호화 키가 없으면 게이트웨이를 부르기 전에 {@link CodefUnavailableException}으로
 * 실패하고 평문으로 대신 저장하지 않는다. 게이트웨이 빈이 없을 때(local·demo 밖)도 같다. 키 없이도 서버는 뜬다.
 *
 * <p>해제는 연결 행(암호화한 connectedId)을 지우고 추가 수집을 멈추는 것이다. CODEF로 들어온 보유 카드·승인 행·확인한 매핑은 지우지 않는다
 * (P4). 데이터 완전 삭제가 아니다.
 */
@Service
public class CodefConnectionService {

	private static final Logger log = LoggerFactory.getLogger(CodefConnectionService.class);

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final CodefConnectionRepository codefConnectionRepository;

	private final ObjectProvider<CodefGateway> gateway;

	private final ConnectedIdKeySource keySource;

	private final TransactionTemplate transactions;

	private final Clock clock;

	/**
	 * {@link Clock} 빈이 하나 있으면 그것을, 없으면 시스템 시계를 쓴다.
	 * @param codefConnectionRepository 연결 저장소
	 * @param gateway CODEF 게이트웨이. 없을 수 있다
	 * @param keySource connectedId 암호화 키 공급
	 * @param transactionManager 저장 트랜잭션
	 * @param clock 연결 시각을 정할 시계
	 */
	@Autowired
	public CodefConnectionService(CodefConnectionRepository codefConnectionRepository,
			ObjectProvider<CodefGateway> gateway, ConnectedIdKeySource keySource,
			PlatformTransactionManager transactionManager, ObjectProvider<Clock> clock) {
		this(codefConnectionRepository, gateway, keySource, new TransactionTemplate(transactionManager),
				clock.getIfUnique(Clock::systemUTC));
	}

	CodefConnectionService(CodefConnectionRepository codefConnectionRepository, ObjectProvider<CodefGateway> gateway,
			ConnectedIdKeySource keySource, TransactionTemplate transactions, Clock clock) {
		this.codefConnectionRepository = Objects.requireNonNull(codefConnectionRepository,
				"codefConnectionRepository");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.keySource = Objects.requireNonNull(keySource, "keySource");
		this.transactions = Objects.requireNonNull(transactions, "transactions");
		this.clock = Objects.requireNonNull(clock, "clock").withZone(SEOUL);
	}

	/** 연결 상태. 연결이 없으면 {@link CodefConnectionView#NOT_CONNECTED}다. */
	public CodefConnectionView get(CurrentUser user) {
		return this.transactions.execute(status -> this.codefConnectionRepository.findById(user.userId())
			.map(CodefConnectionService::toView)
			.orElse(CodefConnectionView.NOT_CONNECTED));
	}

	/**
	 * CODEF 계정을 연결한다. 기관·로그인 방식은 웹 계층이 {@code 0301}·{@code ID}로 검사한 뒤 부른다.
	 * @param user 현재 사용자
	 * @param loginId 카드사 홈페이지 아이디. 저장하지 않는다
	 * @param password 비밀번호. 이 메서드가 끝나면 내용을 지운다
	 * @return 연결 뒤 상태와 최초 연결 여부
	 * @throws CodefPortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws CodefLoginFailedException 카드사 로그인 거절
	 * @throws CodefCardVerificationNotSupportedException 카드소지확인 필요
	 * @throws CodefUnavailableException 그 밖의 실패, 게이트웨이 없음, 암호화 키 없음
	 */
	public CodefConnectResult connect(CurrentUser user, String loginId, char[] password) {
		try {
			requireWritable(user);
			CodefGateway codef = this.gateway.getIfAvailable();
			Optional<ConnectedIdCipher> cipher = cipher();
			if (codef == null || cipher.isEmpty()) {
				throw new CodefUnavailableException();
			}
			CodefRegistration registration = codef.register(CodefConnectionView.KB_KOOKMIN_CARD, loginId, password);
			return switch (registration) {
				case CodefRegistration.Registered registered ->
					store(user, registered.connectedId(), codef, cipher.get());
				case CodefRegistration.LoginRejected rejected -> throw new CodefLoginFailedException();
				case CodefRegistration.CardVerificationRequired required ->
					throw new CodefCardVerificationNotSupportedException();
				case CodefRegistration.Unavailable unavailable -> throw new CodefUnavailableException();
			};
		}
		finally {
			Arrays.fill(password, '\0');
		}
	}

	/**
	 * CODEF 연결을 해제한다. 연결 행을 지우고 커밋 뒤 CODEF 쪽 정리를 요청한다. 조회 데이터는 지우지 않는다(P4).
	 * @param user 현재 사용자
	 * @throws CodefPortfolioReadOnlyException 가상 포트폴리오 사용자
	 * @throws CodefNotConnectedException 연결이 없음
	 */
	public void disconnect(CurrentUser user) {
		requireWritable(user);
		String ciphertext = this.transactions.execute(status -> {
			this.codefConnectionRepository.lockUser(user.userId());
			CodefConnectionEntity existing = this.codefConnectionRepository.lockByUserId(user.userId())
				.orElseThrow(CodefNotConnectedException::new);
			this.codefConnectionRepository.delete(existing);
			return existing.getConnectedIdCiphertext();
		});
		CodefGateway codef = this.gateway.getIfAvailable();
		Optional<ConnectedIdCipher> cipher = cipher();
		if (codef == null || cipher.isEmpty()) {
			log.warn("CODEF connectedId release skipped reason={}", (codef == null) ? "NO_GATEWAY" : "NO_KEY");
			return;
		}
		releaseStored(codef, cipher.get(), user.userId(), ciphertext);
	}

	private CodefConnectResult store(CurrentUser user, String connectedId, CodefGateway codef,
			ConnectedIdCipher cipher) {
		Stored stored;
		try {
			String ciphertext = cipher.encrypt(user.userId(), connectedId);
			stored = this.transactions.execute(status -> {
				this.codefConnectionRepository.lockUser(user.userId());
				LocalDateTime now = LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
				CodefConnectionEntity existing = this.codefConnectionRepository.lockByUserId(user.userId()).orElse(null);
				if (existing != null) {
					String previous = existing.reconnect(ciphertext, now);
					return new Stored(toView(existing), false, previous);
				}
				CodefConnectionEntity created = this.codefConnectionRepository.save(new CodefConnectionEntity(
						user.userId(), CodefConnectionView.KB_KOOKMIN_CARD, ciphertext, now));
				return new Stored(toView(created), true, null);
			});
		}
		catch (RuntimeException ex) {
			release(codef, connectedId);
			throw ex;
		}
		if (stored.previousCiphertext() != null) {
			releaseStored(codef, cipher, user.userId(), stored.previousCiphertext());
		}
		return new CodefConnectResult(stored.view(), stored.created());
	}

	private Optional<ConnectedIdCipher> cipher() {
		return this.keySource.key().map(ConnectedIdCipher::new);
	}

	private static void requireWritable(CurrentUser user) {
		if (user.readOnly()) {
			throw new CodefPortfolioReadOnlyException();
		}
	}

	// 복호화 실패(키 교체 등)도 BeONE 상태를 바꾸지 않는다. 로그에는 예외 클래스 이름만 남긴다
	private static void releaseStored(CodefGateway codef, ConnectedIdCipher cipher, long userId, String ciphertext) {
		String connectedId;
		try {
			connectedId = cipher.decrypt(userId, ciphertext);
		}
		catch (RuntimeException ex) {
			log.warn("CODEF connectedId release skipped exception={}", ex.getClass().getName());
			return;
		}
		release(codef, connectedId);
	}

	// 정리 실패는 BeONE 상태를 바꾸지 않는다. 로그에는 예외 클래스 이름만 남긴다
	private static void release(CodefGateway codef, String connectedId) {
		try {
			codef.release(connectedId);
		}
		catch (RuntimeException ex) {
			log.warn("CODEF connectedId release failed exception={}", ex.getClass().getName());
		}
	}

	private static CodefConnectionView toView(CodefConnectionEntity entity) {
		return new CodefConnectionView(entity.getOrganization(), entity.getStatus(), entity.getConnectedAt(),
				entity.getLastSyncedAt());
	}

	private record Stored(CodefConnectionView view, boolean created, String previousCiphertext) {
	}

}
