package com.beone.api.codef.persistence;

import java.time.LocalDateTime;

import com.beone.api.codef.application.CodefConnectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code codef_connections} 테이블 한 행(Flyway {@code V10__codef_connections.sql}). 사용자당 한 행이고 기본 키가 사용자 ID다.
 *
 * <p>connectedId는 암호문으로만 들고 있다(평문은 서비스가 복호화해 게이트웨이에만 넘긴다). 문자열 표현에 넣지 않는다. 사용자는 ID 값으로만
 * 참조한다.
 */
@Entity
@Table(name = "codef_connections")
public class CodefConnectionEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "organization", nullable = false, length = 8)
	private String organization;

	@Column(name = "connected_id_ciphertext", nullable = false, length = 512)
	private String connectedIdCiphertext;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 32)
	private CodefConnectionStatus status;

	@Column(name = "connected_at", nullable = false)
	private LocalDateTime connectedAt;

	@Column(name = "last_synced_at")
	private LocalDateTime lastSyncedAt;

	protected CodefConnectionEntity() {
	}

	/**
	 * 최초 연결 한 행.
	 * @param userId 사용자 ID
	 * @param organization 기관 코드
	 * @param connectedIdCiphertext 암호화한 connectedId
	 * @param connectedAt 연결 시각(서울)
	 */
	public CodefConnectionEntity(long userId, String organization, String connectedIdCiphertext,
			LocalDateTime connectedAt) {
		this.userId = userId;
		this.organization = organization;
		this.connectedIdCiphertext = connectedIdCiphertext;
		this.status = CodefConnectionStatus.CONNECTED;
		this.connectedAt = connectedAt;
	}

	/**
	 * 재연결 성공. connectedId를 바꾸고 상태를 {@code CONNECTED}로 되돌린다. 마지막 동기화 시각은 그대로 둔다.
	 * @param newCiphertext 새 connectedId 암호문
	 * @param reconnectedAt 재연결 시각(서울)
	 * @return 교체 전 connectedId 암호문
	 */
	public String reconnect(String newCiphertext, LocalDateTime reconnectedAt) {
		String previous = this.connectedIdCiphertext;
		this.connectedIdCiphertext = newCiphertext;
		this.status = CodefConnectionStatus.CONNECTED;
		this.connectedAt = reconnectedAt;
		return previous;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getOrganization() {
		return this.organization;
	}

	public String getConnectedIdCiphertext() {
		return this.connectedIdCiphertext;
	}

	public CodefConnectionStatus getStatus() {
		return this.status;
	}

	public LocalDateTime getConnectedAt() {
		return this.connectedAt;
	}

	public LocalDateTime getLastSyncedAt() {
		return this.lastSyncedAt;
	}

	@Override
	public String toString() {
		return "CodefConnectionEntity[userId=" + this.userId + ", status=" + this.status + "]";
	}

}
