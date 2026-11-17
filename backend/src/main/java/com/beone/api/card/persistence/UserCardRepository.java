package com.beone.api.card.persistence;

import java.util.List;
import java.util.Optional;

import com.beone.api.card.application.MappingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 보유 카드 저장소. 조회는 항상 사용자 ID와 함께 하고 해제한 카드는 찾지 않는다(D-09, 결정 27 A2-2).
 */
public interface UserCardRepository extends JpaRepository<UserCardEntity, Long> {

	/** 이 사용자의 해제하지 않은 카드를 등록 순(ID 오름차순)으로 찾는다. */
	List<UserCardEntity> findByUserIdAndReleasedAtIsNullOrderByUserCardIdAsc(Long userId);

	/**
	 * 이 사용자의 해제하지 않은 카드 하나를 쓰기용으로 찾는다. 다른 사용자의 카드는 찾지 않는다.
	 *
	 * <p>행에 쓰기 잠금을 걸어 같은 카드를 바꾸는 요청을 차례로 처리한다. 뒤 요청은 앞 요청이 커밋한 값을 읽고 검사한다. 트랜잭션
	 * 안에서만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserCardEntity> findByUserCardIdAndUserIdAndReleasedAtIsNull(Long userCardId, Long userId);

	/**
	 * 이 사용자의 해제하지 않은 카드 전부를 ID 오름차순으로 쓰기 잠금한다(#122). 모의 승인이 추천 재계산 전에 부른다.
	 *
	 * <p>{@code idx_user_cards_user_id}(사용자 ID, 기본 키 순) 순서로 읽으며 잠그므로 같은 사용자의 요청들은 항상 같은 순서로 잠가
	 * 서로 교착하지 않는다. 잠금 읽기이므로 이 호출이 트랜잭션의 일관 읽기 시점을 정하지 않는다. 트랜잭션 안에서만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from UserCardEntity c where c.userId = :userId and c.releasedAt is null order by c.userCardId")
	List<UserCardEntity> lockActiveByUserIdOrderByUserCardId(@Param("userId") Long userId);

	/** 이 사용자에게 이 상태·상품의 해제하지 않은 카드가 있는지. 수동 등록의 중복 검사에 쓴다. */
	boolean existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(Long userId, String cardProductId,
			MappingStatus mappingStatus);

	/** 위와 같되 카드 하나를 뺀다. 매핑 변경에서 자기 자신을 중복으로 보지 않는다(결정 27 A2-8). */
	boolean existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNullAndUserCardIdNot(Long userId,
			String cardProductId, MappingStatus mappingStatus, Long userCardId);

	/** 이 사용자의 해제하지 않은 카드 수. */
	long countByUserIdAndReleasedAtIsNull(Long userId);

}
