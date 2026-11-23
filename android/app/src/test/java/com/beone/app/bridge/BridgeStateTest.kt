package com.beone.app.bridge

import com.beone.app.auth.ActivationState
import com.beone.app.auth.AuthMethod
import com.beone.app.auth.AuthRequestPhase
import com.beone.app.auth.InactiveReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 인증 상태 → `state`·`payment.check.result` 필드 매핑. 기대값은 계약 3절의 `reason` 목록과 예시에서 정했다. */
class BridgeStateTest {

    @Test
    fun everyInactiveReasonMapsToContractReason() {
        val expected = mapOf(
            InactiveReason.NOT_AUTHENTICATED to "NOT_AUTHENTICATED",
            InactiveReason.EXPIRED to "EXPIRED",
            InactiveReason.AUTH_FAILED to "AUTH_FAILED",
            InactiveReason.AUTH_CANCELED to "AUTH_CANCELED",
            InactiveReason.LEFT_APP to "LEFT_APP",
            InactiveReason.SETUP_REQUIRED to "SETUP_REQUIRED",
            InactiveReason.UNAVAILABLE to "UNAVAILABLE",
            InactiveReason.NO_RESPONSE to "NO_RESPONSE",
        )
        // 모든 enum 값이 표에 있어야 한다. 새 InactiveReason이 생기면 계약 결정 없이 통과하지 않는다.
        assertEquals(InactiveReason.entries.toSet(), expected.keys)
        expected.forEach { (reason, wire) -> assertEquals(wire, BridgeState.wireReason(reason)) }
    }

    @Test
    fun reasonValueSetEqualsContractSet() {
        val contract = setOf(
            "NOT_AUTHENTICATED", "EXPIRED", "AUTH_FAILED", "AUTH_CANCELED",
            "LEFT_APP", "SETUP_REQUIRED", "UNAVAILABLE", "NO_RESPONSE", "ACTIVE",
        )
        val produced = InactiveReason.entries.map(BridgeState::wireReason).toSet() + BridgeContract.REASON_ACTIVE
        assertEquals(contract, produced)
    }

    @Test
    fun inactiveStateHasZeroRemainingAndNoMethod() {
        InactiveReason.entries.forEach { reason ->
            val state = BridgeState.of(ActivationState.Inactive(reason), AuthRequestPhase.Idle, remainingMillis = 0)
            assertEquals(BridgeState(false, 0, BridgeState.wireReason(reason), null, false), state)
        }
    }

    @Test
    fun activeStateCarriesRemainingAndMethod() {
        val active = ActivationState.Active(expiresAtMillis = 70_000, method = AuthMethod.BIOMETRIC)
        assertEquals(
            BridgeState(true, 60_000, "ACTIVE", "BIOMETRIC", false),
            BridgeState.of(active, AuthRequestPhase.Idle, remainingMillis = 60_000),
        )
        val pin = ActivationState.Active(expiresAtMillis = 1, method = AuthMethod.DEVICE_CREDENTIAL)
        assertEquals("DEVICE_CREDENTIAL", BridgeState.of(pin, AuthRequestPhase.Idle, 1).method)
    }

    @Test
    fun unknownMethodIsOmitted() {
        val active = ActivationState.Active(expiresAtMillis = 1, method = AuthMethod.UNKNOWN)
        val state = BridgeState.of(active, AuthRequestPhase.Idle, 1)
        assertNull(state.method)
        assertEquals(
            """{"v":1,"type":"state","active":true,"remainingMs":1,"reason":"ACTIVE","authenticating":false}""",
            BridgeOutbound.state(state),
        )
    }

    @Test
    fun authenticatingIsTrueUnlessIdle() {
        val inactive = ActivationState.Inactive(InactiveReason.AUTH_FAILED)
        assertFalse(BridgeState.of(inactive, AuthRequestPhase.Idle, 0).authenticating)
        listOf(
            AuthRequestPhase.Authenticating(1),
            AuthRequestPhase.Draining(1),
            AuthRequestPhase.Stalled(1, cancelRequested = false),
            AuthRequestPhase.Stalled(1, cancelRequested = true),
        ).forEach { assertTrue(it.toString(), BridgeState.of(inactive, it, 0).authenticating) }
        // 활성 중 재인증(C3)이면 active와 authenticating이 함께 true일 수 있다.
        val active = ActivationState.Active(expiresAtMillis = 1, method = AuthMethod.BIOMETRIC)
        assertTrue(BridgeState.of(active, AuthRequestPhase.Authenticating(2), 1).authenticating)
    }

    @Test
    fun negativeRemainingIsClampedToZero() {
        val active = ActivationState.Active(expiresAtMillis = 1, method = AuthMethod.BIOMETRIC)
        assertEquals(0L, BridgeState.of(active, AuthRequestPhase.Idle, -5).remainingMs)
    }

    @Test
    fun outboundMessagesMatchContractExamples() {
        assertEquals(
            """{"v":1,"type":"state","active":true,"remainingMs":60000,"reason":"ACTIVE","method":"BIOMETRIC","authenticating":false}""",
            BridgeOutbound.state(BridgeState(true, 60000, "ACTIVE", "BIOMETRIC", false)),
        )
        assertEquals(
            """{"v":1,"type":"state","active":false,"remainingMs":0,"reason":"LEFT_APP","authenticating":false}""",
            BridgeOutbound.state(BridgeState(false, 0, "LEFT_APP", null, false)),
        )
        assertEquals(
            """{"v":1,"type":"payment.check.result","allowed":false,"reason":"EXPIRED"}""",
            BridgeOutbound.paymentCheckResult(BridgeState(false, 0, "EXPIRED", null, false)),
        )
        assertEquals(
            """{"v":1,"type":"error","code":"UNSUPPORTED_VERSION"}""",
            BridgeOutbound.error(BridgeErrorCode.UNSUPPORTED_VERSION),
        )
    }

    @Test
    fun errorCodesAreExactlyThree() {
        assertEquals(
            setOf("UNKNOWN_TYPE", "UNSUPPORTED_VERSION", "INVALID_MESSAGE"),
            BridgeErrorCode.entries.map { it.name }.toSet(),
        )
    }
}
