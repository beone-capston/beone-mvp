package com.beone.api.codef.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CODEF 동기화 기록 저장소.
 */
public interface CodefSyncRepository extends JpaRepository<CodefSyncEntity, Long> {

}
