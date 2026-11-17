package com.beone.api.payment.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 모의 취소 저장소. 결제 소유는 호출자가 먼저 확인한다.
 */
public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellationEntity, Long> {

	/** 한 결제의 취소를 취소 순서대로 찾는다. */
	List<PaymentCancellationEntity> findBySimulatedPaymentIdOrderByCancellationIdAsc(Long simulatedPaymentId);

}
