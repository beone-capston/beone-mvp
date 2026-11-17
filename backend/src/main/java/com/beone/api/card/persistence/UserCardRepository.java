package com.beone.api.card.persistence;

import java.util.List;
import java.util.Optional;

import com.beone.api.card.application.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보유 카드 저장소. 조회는 항상 사용자 ID와 함께 하고 해제한 카드는 찾지 않는다(D-09, 결정 27 A2-2).
 */
public interface UserCardRepository extends JpaRepository<UserCardEntity, Long> {

	/** 이 사용자의 해제하지 않은 카드를 등록 순(ID 오름차순)으로 찾는다. */
	List<UserCardEntity> findByUserIdAndReleasedAtIsNullOrderByUserCardIdAsc(Long userId);

	/** 이 사용자의 해제하지 않은 카드 하나를 찾는다. 다른 사용자의 카드는 찾지 않는다. */
	Optional<UserCardEntity> findByUserCardIdAndUserIdAndReleasedAtIsNull(Long userCardId, Long userId);

	/** 이 사용자에게 이 상태·상품의 해제하지 않은 카드가 있는지. 수동 등록의 중복 검사에 쓴다. */
	boolean existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNull(Long userId, String cardProductId,
			MappingStatus mappingStatus);

	/** 위와 같되 카드 하나를 뺀다. 매핑 변경에서 자기 자신을 중복으로 보지 않는다(결정 27 A2-8). */
	boolean existsByUserIdAndCardProductIdAndMappingStatusAndReleasedAtIsNullAndUserCardIdNot(Long userId,
			String cardProductId, MappingStatus mappingStatus, Long userCardId);

	/** 이 사용자의 해제하지 않은 카드 수. */
	long countByUserIdAndReleasedAtIsNull(Long userId);

}
