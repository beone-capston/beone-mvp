package com.beone.app.bridge

import com.beone.app.auth.ActivationState
import com.beone.app.auth.AuthMethod
import com.beone.app.auth.AuthRequestPhase
import com.beone.app.auth.InactiveReason

/**
 * 웹에 보낼 네이티브 인증 상태(`state` 메시지의 필드). 웹은 이 값을 표시용으로만 쓰고 네이티브가 기준이다.
 * 생체 정보·PIN·오류 원문·기기 식별자는 담지 않는다.
 */
data class BridgeState(
    val active: Boolean,
    val remainingMs: Long,
    val reason: String,
    /** 인증 수단을 알 수 없으면(`UNKNOWN`) null이며 메시지에서 생략한다. */
    val method: String?,
    /** 새 인증 요청을 받을 수 없는 동안(인증 중·정리 중·정지) true. */
    val authenticating: Boolean,
) {

    companion object {
        /** 네이티브 활성 상태·요청 단계·남은 시간을 계약 필드로 옮긴다. 비활성이면 남은 시간은 0이다. */
        fun of(state: ActivationState, phase: AuthRequestPhase, remainingMillis: Long): BridgeState = when (state) {
            is ActivationState.Active -> BridgeState(
                active = true,
                remainingMs = remainingMillis.coerceAtLeast(0L),
                reason = BridgeContract.REASON_ACTIVE,
                method = wireMethod(state.method),
                authenticating = phase != AuthRequestPhase.Idle,
            )
            is ActivationState.Inactive -> BridgeState(
                active = false,
                remainingMs = 0L,
                reason = wireReason(state.reason),
                method = null,
                authenticating = phase != AuthRequestPhase.Idle,
            )
        }

        /** `InactiveReason` → 계약 `reason` 문자열. 이름이 같아도 계약 값을 명시해 enum 이름 변경이 계약을 바꾸지 않게 한다. */
        fun wireReason(reason: InactiveReason): String = when (reason) {
            InactiveReason.NOT_AUTHENTICATED -> "NOT_AUTHENTICATED"
            InactiveReason.EXPIRED -> "EXPIRED"
            InactiveReason.AUTH_FAILED -> "AUTH_FAILED"
            InactiveReason.AUTH_CANCELED -> "AUTH_CANCELED"
            InactiveReason.LEFT_APP -> "LEFT_APP"
            InactiveReason.SETUP_REQUIRED -> "SETUP_REQUIRED"
            InactiveReason.UNAVAILABLE -> "UNAVAILABLE"
            InactiveReason.NO_RESPONSE -> "NO_RESPONSE"
        }

        /** 인증 수단은 `BIOMETRIC`·`DEVICE_CREDENTIAL` 구분까지만 보내고, 모르면 생략한다. */
        fun wireMethod(method: AuthMethod): String? = when (method) {
            AuthMethod.BIOMETRIC -> "BIOMETRIC"
            AuthMethod.DEVICE_CREDENTIAL -> "DEVICE_CREDENTIAL"
            AuthMethod.UNKNOWN -> null
        }
    }
}

/**
 * 네이티브 → 웹 메시지 3종의 JSON 문자열을 만든다. 모든 값이 계약의 ASCII 상수·숫자·불리언이라 이스케이프가 필요 없다.
 * 응답의 `v`는 입력과 무관하게 항상 숫자 1이다.
 */
object BridgeOutbound {

    /** `state` 메시지. */
    fun state(state: BridgeState): String = buildString {
        append("{\"v\":").append(BridgeContract.VERSION)
        append(",\"type\":\"").append(BridgeContract.TYPE_STATE).append('"')
        append(",\"active\":").append(state.active)
        append(",\"remainingMs\":").append(state.remainingMs)
        append(",\"reason\":\"").append(state.reason).append('"')
        state.method?.let { append(",\"method\":\"").append(it).append('"') }
        append(",\"authenticating\":").append(state.authenticating)
        append('}')
    }

    /** `payment.check.result` 메시지. 활성일 때만 허용하며 `reason`은 `state.reason`과 같은 값 집합이다. */
    fun paymentCheckResult(state: BridgeState): String = buildString {
        append("{\"v\":").append(BridgeContract.VERSION)
        append(",\"type\":\"").append(BridgeContract.TYPE_PAYMENT_CHECK_RESULT).append('"')
        append(",\"allowed\":").append(state.active)
        append(",\"reason\":\"").append(state.reason).append('"')
        append('}')
    }

    /** `error` 메시지. */
    fun error(code: BridgeErrorCode): String =
        "{\"v\":${BridgeContract.VERSION},\"type\":\"${BridgeContract.TYPE_ERROR}\",\"code\":\"${code.name}\"}"
}
