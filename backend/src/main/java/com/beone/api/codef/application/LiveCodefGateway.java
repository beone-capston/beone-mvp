package com.beone.api.codef.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.beone.api.codef.CodefAccount;
import com.beone.api.codef.CodefApproval;
import com.beone.api.codef.CodefCardPerformance;
import com.beone.api.codef.CodefHeldCard;
import com.beone.api.codef.CodefList;
import com.beone.api.codef.CodefProductResponse;
import com.beone.api.codef.CodefResultCategory;
import com.beone.api.codef.transport.CodefCallResult;
import com.beone.api.codef.transport.CodefClient;

/**
 * 실 CODEF 게이트웨이(LP-1). 기존 전송 계층({@link CodefClient}·관측)으로 계정 등록·삭제와 상품 3종을 부른다.
 *
 * <p>프로필 {@value #PROFILE}에서만 등록한다. 회원 프로필이 함께 켜져 있고 공개 프로필 {@code prod}가 아닐 때뿐이다(결정 38-8). 실호출은 별도
 * 승인 뒤 비공개 검증 환경에서만 하며, 이 클래스의 시험은 루프백 스텁 HTTP로만 한다.
 *
 * <p>호출마다 관측을 하나 연다. 동기화 한 번은 API마다 한 번씩만 부르고, 실패해도 다시 부르지 않으며 리다이렉트를 따르지 않는다(전송 계층 규칙).
 * 근거 있는 결과 코드가 없어 연결 무효({@link CodefConnectionInvalidException})로 바꾸는 코드는 아직 없다. 상품 응답의 판정은
 * {@code codef.sync}가 한다. 계정 API의 경로·요청·응답은 공식 SDK와 대조했고, 남은 확인 항목은 docs/domains/codef.md에 있다. 비밀번호·connectedId는
 * 로그·예외 문구에
 * 넣지 않는다.
 */
@Component
@Profile(LiveCodefGateway.PROFILE)
public class LiveCodefGateway implements CodefGateway, CodefSyncGateway {

	/** 실 게이트웨이와 회원 CODEF 경로를 켜는 프로필 조건. */
	public static final String PROFILE = "member & codef-live & !prod";

	private static final Logger log = LoggerFactory.getLogger(LiveCodefGateway.class);

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

	/** 공식 문서 예시의 최신순 정렬. 샌드박스 확인 절차와 같다. */
	private static final String ORDER_BY_LATEST = "0";

	/**
	 * 승인내역 조회구분. KB 필수 입력이다(파라미터 시트 {@code 승인내역!H4:H8}, docs/domains/codef-kb.md). 값 {@code "1"}은 공식 예제
	 * {@code codef-io/codef-python}(커밋 {@code c6bec12}) {@code sandbox/cd/TestKR_CD_P002.py}의 값이다. "1"이 전체 카드 조회라는 의미는
	 * 카드번호가 "카드별 조회 시"에만 쓰인다는 시트 주석에서 추정한 가정이며, 실호출로 확정한다. 카드번호는 보내지 않는다.
	 */
	private static final String INQUIRY_ALL_CARDS = "1";

	/**
	 * 가맹점정보 포함여부. KB에서는 선택 입력이다. 공식 예제와 같은 {@code "0"}(미포함)을 보낸다. 가맹점 사업자번호·주소 같은 추가 정보는
	 * 옮기지 않는 값이고, 가맹점명은 기본 출력이라 이 값과 무관하다.
	 */
	private static final String MEMBER_STORE_INFO_EXCLUDED = "0";

	private final CodefClient client;

	private final CodefPasswordEncryptor encryptor;

	/**
	 * 공개키는 환경 변수에서 읽는다. 암호화기 빈이 하나 있으면 그것을 쓴다(테스트의 합성 공개키 주입용).
	 * @param client 전송 계층
	 * @param encryptor 암호화기 빈. 보통 없다
	 */
	@Autowired
	public LiveCodefGateway(CodefClient client, ObjectProvider<CodefPasswordEncryptor> encryptor) {
		this(client, encryptor.getIfUnique(() -> CodefPasswordEncryptor.fromEnvironment(System.getenv())));
	}

	LiveCodefGateway(CodefClient client, CodefPasswordEncryptor encryptor) {
		this.client = Objects.requireNonNull(client, "client");
		this.encryptor = Objects.requireNonNull(encryptor, "encryptor");
	}

	@Override
	public CodefRegistration register(String organization, String loginId, char[] password) {
		Optional<String> encrypted = this.encryptor.encrypt(password);
		if (encrypted.isEmpty()) {
			log.warn("CODEF account create skipped reason={}",
					this.encryptor.isConfigured() ? "ENCRYPTION_FAILED" : "NO_PUBLIC_KEY");
			return new CodefRegistration.Unavailable();
		}
		CodefCallResult<CodefAccount> result = this.client.startObservation()
			.createAccount(organization, loginId, encrypted.get());
		if (!(result instanceof CodefCallResult.Answered<CodefAccount> answered)
				|| !(answered.response() instanceof CodefProductResponse.Interpreted<CodefAccount> interpreted)) {
			return new CodefRegistration.Unavailable();
		}
		CodefResultCategory category = interpreted.response().result().category();
		String connectedId = (interpreted.response().data() instanceof CodefList.Items<CodefAccount> items)
				? items.items().stream().findFirst().flatMap(CodefAccount::connectedId).orElse(null) : null;
		return CodefRegistrations.fromCategory(category, connectedId);
	}

	@Override
	public void release(String connectedId) {
		// MVP 기관은 KB국민카드 하나다. 실패해도 BeONE 상태는 바꾸지 않는다
		CodefCallResult<CodefAccount> result = this.client.startObservation()
			.deleteAccount(CodefConnectionView.KB_KOOKMIN_CARD, connectedId);
		if (!(result instanceof CodefCallResult.Answered<CodefAccount> answered)
				|| !(answered.response() instanceof CodefProductResponse.Interpreted<CodefAccount> interpreted)
				|| interpreted.response().result().category() != CodefResultCategory.SUCCESS) {
			log.warn("CODEF connectedId release not confirmed");
		}
	}

	@Override
	public CodefCallResult<CodefHeldCard> heldCards(String organization, String connectedId) {
		return this.client.startObservation().heldCards(target(organization, connectedId));
	}

	@Override
	public CodefCallResult<CodefApproval> approvals(String organization, String connectedId, LocalDate from,
			LocalDate to) {
		return this.client.startObservation()
			.approvals(Map.of("organization", organization, "connectedId", connectedId, "startDate",
					YYYYMMDD.format(from), "endDate", YYYYMMDD.format(to), "orderBy", ORDER_BY_LATEST, "inquiryType",
					INQUIRY_ALL_CARDS, "memberStoreInfoType", MEMBER_STORE_INFO_EXCLUDED));
	}

	@Override
	public CodefCallResult<CodefCardPerformance> performances(String organization, String connectedId) {
		return this.client.startObservation().cardPerformances(target(organization, connectedId));
	}

	private static Map<String, String> target(String organization, String connectedId) {
		return Map.of("organization", organization, "connectedId", connectedId);
	}

}
