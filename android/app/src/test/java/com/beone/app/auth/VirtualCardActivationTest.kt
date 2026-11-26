package com.beone.app.auth

import com.beone.app.bridge.BridgeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 60초 활성 상태 계산만 검증한다. 어느 요청의 결과인지 판정은 AuthRequestGateTest·ViewModel 테스트가 맡는다.
class VirtualCardActivationTest {

    private var now = 1_000_000L
    private val activation = VirtualCardActivation { now }

    private fun authenticate(outcome: AuthOutcome = AuthOutcome.Succeeded(AuthMethod.BIOMETRIC)): ActivationState =
        activation.onAuthOutcome(outcome)

    private fun inactive(reason: InactiveReason) = ActivationState.Inactive(reason)

    @Test
    fun startsInactiveBeforeAuthentication() {
        assertEquals(inactive(InactiveReason.NOT_AUTHENTICATED), activation.state)
        assertEquals(0L, activation.remainingMillis())
    }

    @Test
    fun successActivatesForSixtySeconds() {
        val state = authenticate(AuthOutcome.Succeeded(AuthMethod.DEVICE_CREDENTIAL))

        assertEquals(ActivationState.Active(now + 60_000L, AuthMethod.DEVICE_CREDENTIAL), state)
        assertEquals(60_000L, activation.remainingMillis())
    }

    @Test
    fun staysActiveOneMillisecondBeforeExpiry() {
        authenticate()
        now += 59_999L

        assertTrue(activation.state is ActivationState.Active)
        assertEquals(1L, activation.remainingMillis())
    }

    @Test
    fun expiresExactlyAtSixtySeconds() {
        authenticate()
        now += 60_000L

        assertEquals(inactive(InactiveReason.EXPIRED), activation.state)
        assertEquals(0L, activation.remainingMillis())
    }

    @Test
    fun expiredStateDoesNotComeBackActive() {
        authenticate()
        now += 60_000L
        activation.state

        now += 1L
        assertEquals(inactive(InactiveReason.EXPIRED), activation.state)
    }

    @Test
    fun reauthenticationWhileActiveRestartsFullSixtySecondsWithoutCarryover() {
        authenticate()
        now += 40_000L

        authenticate()

        assertEquals(60_000L, activation.remainingMillis())
        now += 59_999L
        assertTrue(activation.state is ActivationState.Active)
        now += 1L
        assertEquals(inactive(InactiveReason.EXPIRED), activation.state)
    }

    @Test
    fun reauthenticationAfterExpiryActivatesAgain() {
        authenticate()
        now += 60_000L
        activation.state

        authenticate()

        assertEquals(60_000L, activation.remainingMillis())
    }

    @Test
    fun cancelWhileActiveDeactivatesImmediately() {
        authenticate()
        now += 10_000L

        val state = authenticate(AuthOutcome.Canceled(errorCode = 10))

        assertEquals(inactive(InactiveReason.AUTH_CANCELED), state)
        assertEquals(0L, activation.remainingMillis())
    }

    @Test
    fun failureWhileActiveDeactivatesImmediately() {
        authenticate()

        val state = authenticate(AuthOutcome.Failed(errorCode = 7))

        assertEquals(inactive(InactiveReason.AUTH_FAILED), state)
    }

    @Test
    fun mismatchDeactivatesAndLaterSuccessActivatesAgain() {
        authenticate()

        val state = activation.onAuthOutcome(AuthOutcome.Mismatch)

        assertEquals(inactive(InactiveReason.AUTH_FAILED), state)
        activation.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        assertEquals(60_000L, activation.remainingMillis())
    }

    @Test
    fun setupRequiredDoesNotActivate() {
        val state = authenticate(AuthOutcome.SetupRequired)

        assertEquals(inactive(InactiveReason.SETUP_REQUIRED), state)
    }

    @Test
    fun unavailableDoesNotActivate() {
        val state = authenticate(AuthOutcome.Unavailable(errorCode = 1))

        assertEquals(inactive(InactiveReason.UNAVAILABLE), state)
    }

    @Test
    fun leavingAppWhileActiveDeactivatesImmediately() {
        authenticate()
        now += 5_000L

        activation.onAppLeft(canceledPendingRequest = false)

        assertEquals(inactive(InactiveReason.LEFT_APP), activation.state)
        now -= 5_000L
        assertEquals(inactive(InactiveReason.LEFT_APP), activation.state)
    }

    @Test
    fun returningAfterLeavingRequiresNewAuthentication() {
        authenticate()
        activation.onAppLeft(canceledPendingRequest = false)
        now += 1_000L

        assertEquals(inactive(InactiveReason.LEFT_APP), activation.state)
        authenticate()
        assertEquals(60_000L, activation.remainingMillis())
    }

    @Test
    fun leavingAppWhileInactiveKeepsPreviousReason() {
        authenticate()
        now += 60_000L
        activation.state

        activation.onAppLeft(canceledPendingRequest = false)

        assertEquals(inactive(InactiveReason.EXPIRED), activation.state)
    }

    @Test
    fun leavingAppWithCanceledRequestRecordsLeftAppEvenWhenInactive() {
        authenticate(AuthOutcome.Mismatch)

        activation.onAppLeft(canceledPendingRequest = true)

        assertEquals(inactive(InactiveReason.LEFT_APP), activation.state)
    }

    @Test
    fun noResponseDeactivatesEvenWhileActive() {
        authenticate()
        now += 10_000L

        activation.onNoResponse()

        assertEquals(inactive(InactiveReason.NO_RESPONSE), activation.state)
        assertEquals(0L, activation.remainingMillis())
    }

    // 만료 경계(MVP Android 갭 AG-01, U5). 기대값은 계약에서 정했다: 활성 시간은 인증 성공 시점부터 60초이고(PRD §4.3, D-21 C3),
    // 웹은 remainingMs가 끝나면 state.get으로 만료(EXPIRED)를 받는다(브리지 v1 B5). 따라서 남은 시간이 0이면 활성일 수 없고,
    // 활성이면 남은 시간이 0보다 커야 한다. 시계는 고정값이며, 상태와 남은 시간 읽기 사이에 시계가 움직이는 경우는 다루지 않는다.
    // 요청 단계는 Idle만 쓴다. 활성이면서 인증 중인 조합의 기대값은 정하지 않는다(AG-03 결정 대기).

    @Test
    fun oneMillisecondLeftIsActiveWithPositiveRemaining() {
        authenticate()
        now += 59_999L

        val bridge = BridgeState.of(activation.state, AuthRequestPhase.Idle, activation.remainingMillis())

        assertTrue(bridge.active)
        assertEquals(1L, bridge.remainingMs)
        assertEquals("ACTIVE", bridge.reason)
    }

    @Test
    fun zeroMillisecondsLeftIsExpiredNotActiveWithZero() {
        authenticate()
        now += 60_000L

        val bridge = BridgeState.of(activation.state, AuthRequestPhase.Idle, activation.remainingMillis())

        assertFalse(bridge.active)
        assertEquals(0L, bridge.remainingMs)
        assertEquals("EXPIRED", bridge.reason)
    }

    @Test
    fun neverActiveWithZeroRemainingAroundExpiry() {
        authenticate()
        val start = now

        (59_990L..60_010L).forEach { elapsed ->
            now = start + elapsed
            val state = activation.state
            val remaining = activation.remainingMillis()

            assertEquals("elapsed=$elapsed", elapsed < 60_000L, state is ActivationState.Active)
            assertEquals("elapsed=$elapsed", state is ActivationState.Active, remaining > 0L)
        }
    }
}
