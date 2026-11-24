package com.beone.app.bridge

import com.beone.app.auth.AuthMethod
import com.beone.app.auth.AuthOutcome
import com.beone.app.auth.AuthRequestDispatch
import com.beone.app.auth.AuthResponseTimeouts
import com.beone.app.auth.DeviceAuthenticator
import com.beone.app.auth.VirtualCardActivationViewModel
import com.beone.app.bridge.BridgeMessageHandler.Outcome
import com.beone.app.web.WebOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 브리지 → ViewModel 연결을 가짜 시계·가짜 authenticator로 확인한다. 실기기 인증 창·콜백 순서를 대신하지 않는다.
 * 응답 대기 시간은 화면과 같은 AuthResponseTimeouts 기본값(120초·10초, 근거 없는 잠정값)을 쓴다.
 * 기대 문자열은 계약 3절 메시지 형식과 PRD의 60초 활성 시간에서 정했다.
 */
class ActivationBridgePortTest {

    private var now = 1_000L
    private val viewModel = VirtualCardActivationViewModel({ now }, AuthResponseTimeouts())
    private val authenticator = FakeAuthenticator()
    private val handler = BridgeMessageHandler(setOf(WebOrigin("https", "beonepay.site", 443)), ActivationBridgePort(viewModel, authenticator))

    private fun send(message: String) = handler.handle(message, "https://beonepay.site", isMainFrame = true)

    private fun state(active: Boolean, remainingMs: Long, reason: String, authenticating: Boolean, method: String? = null) =
        Outcome.Reply(
            buildString {
                append("""{"v":1,"type":"state","active":$active,"remainingMs":$remainingMs,"reason":"$reason",""")
                if (method != null) append(""""method":"$method",""")
                append(""""authenticating":$authenticating}""")
            },
        )

    @Test
    fun initialStateIsNotAuthenticated() {
        assertEquals(state(false, 0, "NOT_AUTHENTICATED", false), send(STATE_GET))
    }

    @Test
    fun authRequestStartsAuthenticationAndSuccessActivatesSixtySeconds() {
        assertEquals(state(false, 0, "NOT_AUTHENTICATED", true), send(AUTH_REQUEST))
        assertEquals(1, authenticator.prompts)
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        assertEquals(state(true, 60_000, "ACTIVE", false, "BIOMETRIC"), send(STATE_GET))
        now += 15_000
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"payment.check.result","allowed":true,"reason":"ACTIVE"}"""),
            send(PAYMENT_CHECK),
        )
        assertEquals(state(true, 45_000, "ACTIVE", false, "BIOMETRIC"), send(STATE_GET))
    }

    @Test
    fun activeStateExpiresAfterSixtySecondsWithoutPush() {
        send(AUTH_REQUEST)
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.DEVICE_CREDENTIAL))
        now += 60_000
        assertEquals(state(false, 0, "EXPIRED", false), send(STATE_GET))
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"payment.check.result","allowed":false,"reason":"EXPIRED"}"""),
            send(PAYMENT_CHECK),
        )
    }

    @Test
    fun repeatedAuthRequestWhileAuthenticatingDoesNotPromptAgain() {
        send(AUTH_REQUEST)
        assertEquals(state(false, 0, "NOT_AUTHENTICATED", true), send(AUTH_REQUEST))
        assertEquals(1, authenticator.prompts)
    }

    @Test
    fun authenticatingTimeoutStallsWithNoResponseAndBlocksNewRequests() {
        send(AUTH_REQUEST)
        now += 120_000 - 1
        assertEquals(state(false, 0, "NOT_AUTHENTICATED", true), send(STATE_GET))
        now += 1
        assertEquals(state(false, 0, "NO_RESPONSE", true), send(STATE_GET))
        assertEquals(state(false, 0, "NO_RESPONSE", true), send(AUTH_REQUEST))
        assertEquals(1, authenticator.prompts)
    }

    @Test
    fun lateSuccessAfterTimeoutDoesNotActivateAndReopensRequests() {
        send(AUTH_REQUEST)
        now += 120_000
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        assertEquals(state(false, 0, "NO_RESPONSE", false), send(STATE_GET))
        send(AUTH_REQUEST)
        assertEquals(2, authenticator.prompts)
    }

    @Test
    fun userCancelDeactivatesWithAuthCanceled() {
        send(AUTH_REQUEST)
        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 10))
        assertEquals(state(false, 0, "AUTH_CANCELED", false), send(STATE_GET))
    }

    @Test
    fun failureDeactivatesActiveStateWithAuthFailed() {
        send(AUTH_REQUEST)
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        send(AUTH_REQUEST)
        viewModel.onAuthOutcome(AuthOutcome.Failed(errorCode = 7))
        assertEquals(state(false, 0, "AUTH_FAILED", false), send(STATE_GET))
    }

    @Test
    fun leavingAppWhileAuthenticatingCancelsAndLateResultIsDiscarded() {
        send(AUTH_REQUEST)
        viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)
        assertEquals(1, authenticator.cancels)
        assertEquals(state(false, 0, "LEFT_APP", true), send(STATE_GET))
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        assertEquals(state(false, 0, "LEFT_APP", false), send(STATE_GET))
    }

    @Test
    fun drainingTimeoutKeepsBlockingRequestsUntilFinalResult() {
        send(AUTH_REQUEST)
        viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)
        now += 10_000
        assertEquals(state(false, 0, "LEFT_APP", true), send(AUTH_REQUEST))
        assertEquals(1, authenticator.prompts)
        viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))
        assertEquals(state(false, 0, "LEFT_APP", false), send(STATE_GET))
    }

    @Test
    fun configurationChangeKeepsActiveState() {
        send(AUTH_REQUEST)
        viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        viewModel.onScreenStopped(isChangingConfigurations = true, authenticator)
        assertEquals(state(true, 60_000, "ACTIVE", false, "BIOMETRIC"), send(STATE_GET))
    }

    @Test
    fun setupRequiredDeviceIsNotPrompted() {
        authenticator.blocked = AuthOutcome.SetupRequired
        assertEquals(state(false, 0, "SETUP_REQUIRED", false), send(AUTH_REQUEST))
        assertEquals(0, authenticator.prompts)
    }

    @Test
    fun requestNotSentToLibraryStaysIdle() {
        authenticator.dispatch = AuthRequestDispatch.NOT_SENT
        assertEquals(state(false, 0, "NOT_AUTHENTICATED", false), send(AUTH_REQUEST))
    }

    // #180 재현: 시계를 읽을 때마다 1ms씩 진행하는 단계 시계로, 상태를 읽은 뒤 남은 시간을 읽기 전에 만료 시점을 넘긴다.
    // 기대값은 계약에서 정했다: 활성이면 남은 시간 > 0, 남은 시간 0이면 비활성(PRD §4.3, D-21 C3, 브리지 v1 B5).
    // 시작 시점은 만료 2ms 전이므로 한 시점으로 계산하면 활성·남은 시간 1ms 또는 비활성·0 중 하나여야 한다.
    @Test
    fun stateAndRemainingComeFromOneClockReadWhenExpiryPassesBetweenReads() {
        var clock = 1_000L
        var tick = 0L
        val steppingViewModel = VirtualCardActivationViewModel(
            {
                val read = clock
                clock += tick
                read
            },
            AuthResponseTimeouts(),
        )
        val port = ActivationBridgePort(steppingViewModel, FakeAuthenticator())
        port.requestAuthentication()
        steppingViewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        val expiresAt = 1_000L + 60_000L
        clock = expiresAt - 2
        tick = 1

        val bridge = port.currentState()

        assertEquals("active=${bridge.active} remainingMs=${bridge.remainingMs}", bridge.active, bridge.remainingMs > 0L)
        assertEquals(BridgeState(true, 1L, "ACTIVE", "BIOMETRIC", false), bridge)
    }

    @Test
    fun jsObjectNameIsUserDecision() {
        // 사용자 결정 2026-11-16: window.BeoneBridge.postMessage(...)
        assertEquals("BeoneBridge", BridgeContract.JS_OBJECT_NAME)
    }

    private class FakeAuthenticator : DeviceAuthenticator {
        var blocked: AuthOutcome? = null
        var dispatch = AuthRequestDispatch.SENT_TO_LIBRARY
        var prompts = 0
        var cancels = 0

        override fun availability(): AuthOutcome? = blocked

        override fun authenticate(): AuthRequestDispatch {
            prompts++
            return dispatch
        }

        override fun cancel() {
            cancels++
        }
    }

    private companion object {
        const val AUTH_REQUEST = """{"v":1,"type":"auth.request"}"""
        const val STATE_GET = """{"v":1,"type":"state.get"}"""
        const val PAYMENT_CHECK = """{"v":1,"type":"payment.check"}"""
    }
}
