package com.beone.api.user.persistence;

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
 * {@code users} 테이블 한 행. 시드로 넣은 시연 사용자이며 애플리케이션은 읽기만 한다.
 *
 * <p>열거형은 MySQL ENUM이 아닌 VARCHAR 열에 이름으로 저장한다(Flyway {@code V2__demo_users.sql}).
 */
@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "demo_user", nullable = false, length = 32, unique = true)
	private DemoUser demoUser;

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

	public Long getUserId() {
		return this.userId;
	}

	public DemoUser getDemoUser() {
		return this.demoUser;
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
