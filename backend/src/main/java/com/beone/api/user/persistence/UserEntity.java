package com.beone.api.user.persistence;

import java.util.Objects;

import com.beone.api.user.application.DemoUser;
import com.beone.api.user.application.PortfolioType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code users} 테이블 한 행. 업무 데이터의 주인(장부)이며 업무 테이블은 {@code user_id}로 이 행을 참조한다.
 *
 * <p>행은 두 종류다. 시드로 넣은 시연 사용자 2명은 {@code demoUser}만 있고 애플리케이션은 읽기만 한다(Flyway {@code V2}). 회원 장부는
 * {@code memberId}만 있고 이메일 인증 완료 때 만든다(Flyway {@code V14}, 결정 37 D1a). 열거형은 MySQL ENUM이 아닌 VARCHAR 열에 이름으로
 * 저장한다.
 */
@Entity
@Table(name = "users")
public class UserEntity {

	/** 회원 장부의 표시 이름(결정 37 D7). */
	public static final String MEMBER_DISPLAY_NAME = "내 카드";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "demo_user", length = 32, unique = true)
	private DemoUser demoUser;

	@Column(name = "member_id", length = 36, unique = true)
	private String memberId;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "portfolio_type", nullable = false, length = 16)
	private PortfolioType portfolioType;

	@Column(name = "read_only", nullable = false)
	private boolean readOnly;

	protected UserEntity() {
	}

	/**
	 * 회원의 '내 카드' 장부를 만든다(결정 37 D1a·D7). 표시 이름은 '내 카드', 실제 보유 카드, 쓰기 가능이다.
	 * @param memberId 회원 ID
	 * @return 저장 전 회원 장부
	 */
	public static UserEntity memberCards(String memberId) {
		UserEntity entity = new UserEntity();
		entity.memberId = Objects.requireNonNull(memberId, "memberId");
		entity.displayName = MEMBER_DISPLAY_NAME;
		entity.portfolioType = PortfolioType.REAL;
		entity.readOnly = false;
		return entity;
	}

	public Long getUserId() {
		return this.userId;
	}

	/**
	 * 시연 사용자 구분.
	 * @return 시연 사용자 구분, 회원 장부면 {@code null}
	 */
	public DemoUser getDemoUser() {
		return this.demoUser;
	}

	/**
	 * 회원 ID.
	 * @return 회원 ID, 시연 사용자면 {@code null}
	 */
	public String getMemberId() {
		return this.memberId;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public PortfolioType getPortfolioType() {
		return this.portfolioType;
	}

	public boolean isReadOnly() {
		return this.readOnly;
	}

}
