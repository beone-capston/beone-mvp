package com.beone.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 가짜 authenticator로 상태 기계의 계약만 검증한다.
// 실제 BiometricPrompt 콜백 도착·순서, 인증 창 표시, OS 동작을 보장하거나 대신하지 않는다.
class VirtualCardActivationViewModelTest {

    private class FakeDeviceAuthenticator(var blocked: AuthOutcome? = null) : DeviceAuthenticator {
        var promptCount = 0
        var cancelCount = 0
        var dispatch = AuthRequestDispatch.SENT_TO_LIBRARY

        // 라이브러리 호출 안에서 결과가 동기로 도착하는 경우를 흉내 낸다.
        var onAuthenticate: () -> Unit = {}
        var onCancel: () -> Unit = {}

        override fun availability(): AuthOutcome? = blocked

        override fun authenticate(): AuthRequestDispatch {
            promptCount++
            onAuthenticate()
            return dispatch
        }

        override fun cancel() {
            cancelCount++
            onCancel()
        }
    }

    private var now = 5_000L
    private val timeouts = AuthResponseTimeouts(authenticatingMillis = 30_000L, drainingMillis = 5_000L)
    private val viewModel = VirtualCardActivationViewModel({ now }, timeouts)
    private val authenticator = FakeDeviceAuthenticator()

    private fun succeed(method: AuthMethod = AuthMethod.BIOMETRIC) = viewModel.onAuthOutcome(AuthOutcome.Succeeded(method))

    private fun leaveApp() = viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)

    private fun startThenLeave() {
        assertTrue(viewModel.requestAuthentication(authenticator))
        leaveApp()
    }

    private val leftApp = ActivationState.Inactive(InactiveReason.LEFT_APP)

    // E1·E3
    @Test
    fun requestIsSentOnceWhileAuthenticating() {
        assertTrue(viewModel.requestAuthentication(authenticator))
        assertFalse(viewModel.requestAuthentication(authenticator))

        assertEquals(1, authenticator.promptCount)
        assertEquals(AuthRequestPhase.Authenticating(requestId = 1), viewModel.requestPhase)
    }

    // E2
    @Test
    fun successWhileAuthenticatingActivatesForSixtySeconds() {
        viewModel.requestAuthentication(authenticator)

        succeed()

        assertEquals(60_000L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // 활성 중에도 대기 단계면 재인증 요청을 라이브러리에 보내고, 성공하면 남은 시간 없이 새 60초로 시작한다.
    @Test
    fun requestWhileActiveIsSentAndSuccessRestartsSixtySeconds() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        now += 40_000L

        assertTrue(viewModel.requestAuthentication(authenticator))
        assertEquals(2, authenticator.promptCount)
        assertEquals(20_000L, viewModel.remainingMillis())

        succeed(AuthMethod.DEVICE_CREDENTIAL)

        assertEquals(ActivationState.Active(expiresAtMillis = now + 60_000L, method = AuthMethod.DEVICE_CREDENTIAL), viewModel.state)
        assertEquals(60_000L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E4
    @Test
    fun requestNotSentToLibraryReturnsToIdle() {
        authenticator.dispatch = AuthRequestDispatch.NOT_SENT

        assertFalse(viewModel.requestAuthentication(authenticator))
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)

        authenticator.dispatch = AuthRequestDispatch.SENT_TO_LIBRARY
        assertTrue(viewModel.requestAuthentication(authenticator))
        succeed()
        assertEquals(2, authenticator.promptCount)
        assertEquals(60_000L, viewModel.remainingMillis())
    }

    // E5
    @Test
    fun deviceWithoutAnyCredentialIsNotPromptedAndAsksForSetup() {
        authenticator.blocked = AuthOutcome.SetupRequired

        assertFalse(viewModel.requestAuthentication(authenticator))

        assertEquals(0, authenticator.promptCount)
        assertEquals(ActivationState.Inactive(InactiveReason.SETUP_REQUIRED), viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E5
    @Test
    fun unavailableDeviceIsNotPrompted() {
        authenticator.blocked = AuthOutcome.Unavailable(errorCode = 1)

        assertFalse(viewModel.requestAuthentication(authenticator))

        assertEquals(0, authenticator.promptCount)
        assertEquals(ActivationState.Inactive(InactiveReason.UNAVAILABLE), viewModel.state)
        authenticator.blocked = null
        assertTrue(viewModel.requestAuthentication(authenticator))
    }

    // E6
    @Test
    fun mismatchThenFinalResultOfSameRequestIsApplied() {
        viewModel.requestAuthentication(authenticator)

        viewModel.onAuthOutcome(AuthOutcome.Mismatch)
        assertEquals(ActivationState.Inactive(InactiveReason.AUTH_FAILED), viewModel.state)
        assertTrue(viewModel.requestPhase is AuthRequestPhase.Authenticating)

        succeed()
        assertEquals(60_000L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    @Test
    fun configurationChangeStopDoesNotDeactivate() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        now += 10_000L

        viewModel.onScreenStopped(isChangingConfigurations = true, authenticator)

        assertEquals(50_000L, viewModel.remainingMillis())
    }

    @Test
    fun configurationChangeDoesNotExtendTime() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        now += 30_000L
        viewModel.onScreenStopped(isChangingConfigurations = true, authenticator)
        now += 30_000L

        assertEquals(ActivationState.Inactive(InactiveReason.EXPIRED), viewModel.state)
    }

    // E14
    @Test
    fun resultThroughRecreatedAuthenticatorAfterRotationBelongsToRequestInProgress() {
        viewModel.requestAuthentication(authenticator)
        viewModel.onScreenStopped(isChangingConfigurations = true, authenticator)
        val recreated = FakeDeviceAuthenticator()
        assertTrue(viewModel.requestPhase is AuthRequestPhase.Authenticating)

        // 재생성된 화면의 새 authenticator 콜백이 같은 ViewModel로 결과를 넘긴다.
        succeed(AuthMethod.DEVICE_CREDENTIAL)

        assertTrue(viewModel.state is ActivationState.Active)
        assertEquals(0, authenticator.cancelCount)
        assertTrue(viewModel.requestAuthentication(recreated))
        assertEquals(1, recreated.promptCount)
    }

    @Test
    fun appLeaveWhileActiveDeactivatesImmediately() {
        viewModel.requestAuthentication(authenticator)
        succeed()

        leaveApp()

        assertEquals(leftApp, viewModel.state)
    }

    // E7: 상태를 정리 중으로 바꾼 뒤 취소를 요청한다.
    @Test
    fun appLeaveWhileAuthenticatingMovesToDrainingBeforeCancel() {
        viewModel.requestAuthentication(authenticator)
        var phaseAtCancel: AuthRequestPhase? = null
        authenticator.onCancel = { phaseAtCancel = viewModel.requestPhase }

        leaveApp()

        assertEquals(1, authenticator.cancelCount)
        assertEquals(AuthRequestPhase.Draining(requestId = 1), phaseAtCancel)
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)
        assertEquals(leftApp, viewModel.state)
    }

    // E8: FIFO에서 창 없이 인증 중으로 남던 순서. 정리 중에는 요청 자체를 보내지 않는다.
    @Test
    fun requestWhileDrainingIsRejectedWithoutCallingLibrary() {
        startThenLeave()

        assertFalse(viewModel.requestAuthentication(authenticator))

        assertEquals(1, authenticator.promptCount)
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)
    }

    // E9
    @Test
    fun lateSuccessWhileDrainingDoesNotActivateAndReturnsToIdle() {
        startThenLeave()
        now += 1_000L

        succeed(AuthMethod.DEVICE_CREDENTIAL)

        assertEquals(leftApp, viewModel.state)
        assertEquals(0L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)

        now += 2_000L
        assertTrue(viewModel.requestAuthentication(authenticator))
        succeed()
        assertEquals(2, authenticator.promptCount)
        assertEquals(ActivationState.Active(now + 60_000L, AuthMethod.BIOMETRIC), viewModel.state)
    }

    // E10
    @Test
    fun lateFailureOrCancelWhileDrainingKeepsLeftAppAndReturnsToIdle() {
        startThenLeave()
        viewModel.onAuthOutcome(AuthOutcome.Failed(errorCode = 7))
        assertEquals(leftApp, viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)

        startThenLeave()
        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))
        assertEquals(leftApp, viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E11
    @Test
    fun mismatchWhileDrainingIsDiscardedAndOnlyFinalResultEndsDraining() {
        startThenLeave()

        viewModel.onAuthOutcome(AuthOutcome.Mismatch)
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)
        assertEquals(leftApp, viewModel.state)

        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
        assertEquals(leftApp, viewModel.state)
    }

    // E12
    @Test
    fun repeatedLeaveWhileDrainingDoesNotCancelAgain() {
        startThenLeave()

        leaveApp()
        leaveApp()

        assertEquals(1, authenticator.cancelCount)
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)
    }

    // E13
    @Test
    fun rotationWhileDrainingKeepsDrainingUntilFinalResult() {
        startThenLeave()

        viewModel.onScreenStopped(isChangingConfigurations = true, FakeDeviceAuthenticator())
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)

        succeed()
        assertEquals(1, authenticator.cancelCount)
        assertEquals(leftApp, viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E15
    @Test
    fun duplicateFinalResultAfterIdleDoesNotChangeStateOrExpiry() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        val activated = viewModel.state
        now += 10_000L

        succeed()
        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))

        assertEquals(activated, viewModel.state)
        assertEquals(50_000L, viewModel.remainingMillis())
    }

    @Test
    fun resultWithoutAnyRequestIsIgnored() {
        assertEquals(ActivationState.Inactive(InactiveReason.NOT_AUTHENTICATED), succeed())
    }

    // E16
    @Test
    fun drainingWithoutResultStallsAfterDrainingTimeout() {
        startThenLeave()

        now += 5_000L

        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = true), viewModel.requestPhase)
        assertFalse(viewModel.requestAuthentication(authenticator))
        assertEquals(1, authenticator.promptCount)
        assertEquals(leftApp, viewModel.state)
    }

    // E17: 창이 실제로 뜨지 않아 결과가 오지 않는 경우를 결과 없음으로 모델링한다.
    @Test
    fun authenticatingWithoutResultStallsAndDeactivatesWithoutCancel() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        assertTrue(viewModel.requestAuthentication(authenticator))

        now += 30_000L

        assertEquals(AuthRequestPhase.Stalled(requestId = 2, cancelRequested = false), viewModel.requestPhase)
        assertEquals(ActivationState.Inactive(InactiveReason.NO_RESPONSE), viewModel.state)
        assertEquals(0, authenticator.cancelCount)
        assertFalse(viewModel.requestAuthentication(authenticator))
        assertEquals(2, authenticator.promptCount)
    }

    // E18
    @Test
    fun lateFinalSuccessWhileStalledDoesNotActivateAndReturnsToIdle() {
        viewModel.requestAuthentication(authenticator)
        now += 30_000L
        assertTrue(viewModel.requestPhase is AuthRequestPhase.Stalled)

        succeed()

        assertEquals(ActivationState.Inactive(InactiveReason.NO_RESPONSE), viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
        assertTrue(viewModel.requestAuthentication(authenticator))
        assertEquals(2, authenticator.promptCount)
    }

    // E18: 정리 중에서 정지된 경우
    @Test
    fun lateFinalResultAfterDrainingStallReturnsToIdleWithoutActivation() {
        startThenLeave()
        now += 5_000L

        succeed()

        assertEquals(leftApp, viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E19
    @Test
    fun mismatchWhileStalledIsDiscarded() {
        viewModel.requestAuthentication(authenticator)
        now += 30_000L

        viewModel.onAuthOutcome(AuthOutcome.Mismatch)

        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = false), viewModel.requestPhase)
        assertEquals(ActivationState.Inactive(InactiveReason.NO_RESPONSE), viewModel.state)
    }

    // E20
    @Test
    fun timeAloneNeverReturnsToIdleOrActivates() {
        startThenLeave()
        now += 1_000L
        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)

        now += 24L * 60 * 60 * 1000

        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = true), viewModel.requestPhase)
        assertFalse(viewModel.requestAuthentication(authenticator))
        assertEquals(leftApp, viewModel.state)
        assertEquals(1, authenticator.promptCount)
    }

    // E21
    @Test
    fun successOneMillisecondBeforeAuthenticatingTimeoutIsApplied() {
        viewModel.requestAuthentication(authenticator)
        now += 29_999L

        succeed()

        assertTrue(viewModel.state is ActivationState.Active)
    }

    // E21: 만료 시각에 도착한 결과는 정지로 먼저 바뀐 뒤 처리되어 버려진다.
    @Test
    fun successArrivingAtAuthenticatingTimeoutIsDiscarded() {
        viewModel.requestAuthentication(authenticator)
        now += 30_000L

        succeed()

        assertEquals(ActivationState.Inactive(InactiveReason.NO_RESPONSE), viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E22
    @Test
    fun leaveWhileStalledWithoutCancelRequestsCancelOnce() {
        viewModel.requestAuthentication(authenticator)
        now += 30_000L
        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = false), viewModel.requestPhase)

        leaveApp()
        leaveApp()

        assertEquals(1, authenticator.cancelCount)
        assertEquals(AuthRequestPhase.Stalled(requestId = 1, cancelRequested = true), viewModel.requestPhase)
        assertEquals(leftApp, viewModel.state)
    }

    // E23
    @Test
    fun synchronousResultInsideAuthenticateCannotStartNestedRequest() {
        var nestedRequest: Boolean? = null
        authenticator.onAuthenticate = {
            succeed()
            nestedRequest = viewModel.requestAuthentication(authenticator)
        }

        assertTrue(viewModel.requestAuthentication(authenticator))

        assertEquals(false, nestedRequest)
        assertEquals(1, authenticator.promptCount)
        assertEquals(60_000L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E24
    @Test
    fun synchronousCancelResultInsideCancelEndsDrainingWithoutNestedRequest() {
        viewModel.requestAuthentication(authenticator)
        var nestedRequest: Boolean? = null
        authenticator.onCancel = {
            viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))
            nestedRequest = viewModel.requestAuthentication(authenticator)
        }

        leaveApp()

        assertEquals(false, nestedRequest)
        assertEquals(1, authenticator.promptCount)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
        assertEquals(leftApp, viewModel.state)
    }

    // E25
    @Test
    fun synchronousFinalResultFollowedByNotSentKeepsIdleFromCallback() {
        authenticator.onAuthenticate = { viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 10)) }
        authenticator.dispatch = AuthRequestDispatch.NOT_SENT

        assertFalse(viewModel.requestAuthentication(authenticator))

        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
        assertEquals(ActivationState.Inactive(InactiveReason.AUTH_CANCELED), viewModel.state)
    }

    // E26
    @Test
    fun appLeaveWithoutPendingRequestDoesNotCancel() {
        viewModel.requestAuthentication(authenticator)
        succeed()

        leaveApp()

        assertEquals(0, authenticator.cancelCount)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E27
    @Test
    fun repeatedLeaveAndReturnWithoutResultThenCancelResultAllowsNextRequest() {
        startThenLeave()
        assertFalse(viewModel.requestAuthentication(authenticator))
        leaveApp()
        assertFalse(viewModel.requestAuthentication(authenticator))

        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))

        assertEquals(1, authenticator.cancelCount)
        assertTrue(viewModel.requestAuthentication(authenticator))
        assertEquals(2, authenticator.promptCount)
    }

    // E28
    @Test
    fun drainingTimeoutIsCountedFromLeaveNotFromRequest() {
        viewModel.requestAuthentication(authenticator)
        now += 29_000L
        leaveApp()

        now += 2_000L

        assertEquals(AuthRequestPhase.Draining(requestId = 1), viewModel.requestPhase)
    }

    // E29 한계 노출(R-b): 바람직한 동작이 아니다. 이전 세션이 성공 뒤 또 성공을 보낸다면(U2, 미확인)
    // 콜백에 요청 식별자가 없어 새 요청의 결과와 구분할 수 없고 새 요청 결과로 적용된다.
    @Test
    fun limitationDuplicateSuccessOfPreviousSessionIsIndistinguishableDuringNextRequest() {
        viewModel.requestAuthentication(authenticator)
        succeed()
        now += 10_000L
        assertTrue(viewModel.requestAuthentication(authenticator))

        succeed()

        assertEquals(60_000L, viewModel.remainingMillis())
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }

    // E30
    @Test
    fun lateFailureAfterAuthenticatingTimeoutKeepsNoResponseReason() {
        viewModel.requestAuthentication(authenticator)
        now += 30_000L

        viewModel.onAuthOutcome(AuthOutcome.Failed(errorCode = 7))

        assertEquals(ActivationState.Inactive(InactiveReason.NO_RESPONSE), viewModel.state)
        assertEquals(AuthRequestPhase.Idle, viewModel.requestPhase)
    }
}
