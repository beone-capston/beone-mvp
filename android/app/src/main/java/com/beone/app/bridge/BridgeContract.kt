package com.beone.app.bridge

/**
 * 브리지 계약 v1의 고정값(docs/android/bridge-contract-candidate.md 3절).
 * 값을 바꾸면 공개 계약이 바뀌므로 사용자 결정 없이 고치지 않는다.
 */
object BridgeContract {

    /** 지원하는 계약 버전. 모든 응답의 `v`도 이 값이다. */
    const val VERSION = 1

    /** 웹 → 네이티브 메시지 크기 상한. Kotlin `String.length`(UTF-16 코드 단위) 기준이며 이 값까지 허용한다. */
    const val MAX_MESSAGE_LENGTH = 4096

    const val TYPE_AUTH_REQUEST = "auth.request"
    const val TYPE_STATE_GET = "state.get"
    const val TYPE_PAYMENT_CHECK = "payment.check"

    const val TYPE_STATE = "state"
    const val TYPE_PAYMENT_CHECK_RESULT = "payment.check.result"
    const val TYPE_ERROR = "error"

    /** 활성 상태일 때의 `reason` 값. */
    const val REASON_ACTIVE = "ACTIVE"

    /**
     * 웹이 `window.BeoneBridge.postMessage(...)`로 부르는 JS 객체 이름. 대소문자까지 같아야 한다.
     * 사용자 결정 2026-11-16(decisions/20 D절)이며 계약 문서 보충은 PR #87에서 할 예정이다.
     */
    const val JS_OBJECT_NAME = "BeoneBridge"
}

/** 웹이 보낼 수 있는 요청 3종. */
enum class BridgeRequest(val type: String) {
    AUTH_REQUEST(BridgeContract.TYPE_AUTH_REQUEST),
    STATE_GET(BridgeContract.TYPE_STATE_GET),
    PAYMENT_CHECK(BridgeContract.TYPE_PAYMENT_CHECK),
    ;

    companion object {
        /** 계약에 있는 웹 → 네이티브 `type`이면 그 요청, 아니면 null. */
        fun fromType(type: String): BridgeRequest? = entries.firstOrNull { it.type == type }
    }
}

/** `error` 메시지의 `code`. 처리를 거부한 사유이며 이 3종 외의 코드는 없다. */
enum class BridgeErrorCode {
    UNKNOWN_TYPE,
    UNSUPPORTED_VERSION,
    INVALID_MESSAGE,
}
