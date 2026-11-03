package com.beone.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// 요청 단계 전이만 검증한다. 실제 라이브러리 콜백·창 표시·OS 보장이 아니다.
class AuthRequestGateTest {

    private var now = 1_000L
    private var stalledNotices = 0
    private val gate = AuthRequestGate(
        elapsedRealtimeMillis = { now },
        timeouts = AuthResponseTimeouts(authenticatingMillis = 30_000L, drainingMillis = 5_000L),
        onAuthenticatingStalled = { stalledNotices++ },
    )

    @Test
    fun beginOnlyFromIdleWithIncreasingRequestId() {
        assertEquals(1L, gate.begin())
        assertNull(gate.begin())

        gate.accept(AuthOutcome.Canceled(errorCode = 10))
        assertEquals(2L, gate.begin())
    }

    @Test
    fun withdrawUnsentIgnoresOtherRequest() {
        val first = requireNotNull(gate.begin())
        gate.accept(AuthOutcome.Canceled(errorCode = 10))
        val second = requireNotNull(gate.begin())

        gate.withdrawUnsent(first)
        assertEquals(AuthRequestPhase.Authenticating(second), gate.phase)

        gate.withdrawUnsent(second)
        assertEquals(AuthRequestPhase.Idle, gate.phase)
    }

    @Test
    fun onlyAuthenticatingResultsAreApplied() {
        assertFalse(gate.accept(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC)))

        gate.begin()
        assertTrue(gate.accept(AuthOutcome.Mismatch))
        assertTrue(gate.accept(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC)))
        assertFalse(gate.accept(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC)))
    }

    @Test
    fun leaveRequestsCancelOnlyOncePerRequest() {
        assertFalse(gate.onAppLeft())

        gate.begin()
        assertTrue(gate.onAppLeft())
        assertFalse(gate.onAppLeft())
        assertEquals(AuthRequestPhase.Draining(requestId = 1), gate.phase)
    }

    @Test
    fun authenticatingTimeoutNotifiesOnceAndKeepsRequestOwnership() {
        gate.begin()
        now += 30_000L

        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = false), gate.phase)
        gate.refresh()
        assertEquals(1, stalledNotices)
        assertNull(gate.begin())
    }

    @Test
    fun drainingTimeoutDoesNotNotifyAuthenticatingStall() {
        gate.begin()
        gate.onAppLeft()
        now += 5_000L

        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = true), gate.phase)
        assertEquals(0, stalledNotices)
    }

    @Test
    fun onlyFinalResultEndsStall() {
        gate.begin()
        now += 30_000L

        assertFalse(gate.accept(AuthOutcome.Mismatch))
        assertTrue(gate.phase is AuthRequestPhase.Stalled)
        assertFalse(gate.accept(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC)))
        assertEquals(AuthRequestPhase.Idle, gate.phase)
    }

    @Test
    fun defaultTimeoutsAreTheProposedValues() {
        assertEquals(AuthResponseTimeouts(authenticatingMillis = 120_000L, drainingMillis = 10_000L), AuthResponseTimeouts())
    }

    @Test
    fun timeoutsMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { AuthResponseTimeouts(authenticatingMillis = 0L, drainingMillis = 5_000L) }
        assertThrows(IllegalArgumentException::class.java) { AuthResponseTimeouts(authenticatingMillis = 30_000L, drainingMillis = -1L) }
    }
}
