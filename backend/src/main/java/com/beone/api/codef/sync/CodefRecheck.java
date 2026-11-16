package com.beone.api.codef.sync;

/**
 * 받아들인 행을 이전 기록(키 → 종류)과 맞춘 결과(결정 R2). 저장은 호출자(업무 서비스, 팀원 담당)가 한다.
 */
public enum CodefRecheck {

	/** 이전 기록에 없는 키다. */
	NEW,

	/** 같은 키·같은 종류로 이미 기록됐다. 다시 넣지 않는다. */
	ALREADY_RECORDED,

	/** 정상 승인으로 기록된 키가 이번에 Type1 전액 취소로 바뀌었다. 같은 승인에 연결된 취소로 기록한다. */
	CANCELED_SINCE_RECORDED

}
