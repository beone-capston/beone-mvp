package com.beone.app.auth

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beone.app.R

// debug 빌드 전용 실기기 점검 화면. 제품 화면이 아니며 release에는 포함되지 않는다.
class DeviceAuthCheckActivity : AppCompatActivity() {

    private lateinit var viewModel: VirtualCardActivationViewModel
    private lateinit var authenticator: DeviceAuthenticator
    private lateinit var statusText: TextView
    private lateinit var remainingText: TextView
    private lateinit var activeIndicator: Button
    private lateinit var authenticateButton: Button
    private val tracer = DebugAuthTrace.tracer
    private val instance = DebugAuthTrace.nextInstance()

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ticker.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        traceLifecycle("ON_CREATE", "created" to if (savedInstanceState == null) "FIRST" else "RECREATED")
        setContentView(R.layout.activity_device_auth_check)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.check_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this, ExperimentViewModelFactory)[VirtualCardActivationViewModel::class.java]
        // 재생성마다 새로 만들어야 진행 중이던 인증 결과를 이어 받는다.
        authenticator = BiometricDeviceAuthenticator(this, tracer) { outcome ->
            viewModel.onAuthOutcome(outcome)
            render()
        }
        statusText = findViewById(R.id.check_status)
        remainingText = findViewById(R.id.check_remaining)
        activeIndicator = findViewById(R.id.check_active_indicator)
        authenticateButton = findViewById(R.id.check_authenticate)
        authenticateButton.setOnClickListener { requestAuthentication() }
        findViewById<TextView>(R.id.check_timeout_notice).text = getString(
            R.string.check_timeout_notice,
            RESPONSE_TIMEOUTS.authenticatingMillis / 1000,
            RESPONSE_TIMEOUTS.drainingMillis / 1000,
        )

        // PRD §4.3-1: 화면을 처음 열면 인증을 요청한다.
        if (savedInstanceState == null) {
            requestAuthentication()
        }
    }

    override fun onStart() {
        super.onStart()
        traceLifecycle("ON_START")
        ticker.post(tick)
    }

    override fun onResume() {
        super.onResume()
        traceLifecycle("ON_RESUME")
    }

    override fun onPause() {
        traceLifecycle("ON_PAUSE")
        super.onPause()
    }

    override fun onStop() {
        traceLifecycle("ON_STOP", "changingConfig" to isChangingConfigurations.toString())
        ticker.removeCallbacks(tick)
        viewModel.onScreenStopped(isChangingConfigurations, authenticator)
        super.onStop()
    }

    override fun onDestroy() {
        traceLifecycle(
            "ON_DESTROY",
            "changingConfig" to isChangingConfigurations.toString(),
            "finishing" to isFinishing.toString(),
        )
        super.onDestroy()
    }

    private fun traceLifecycle(callback: String, vararg extra: Pair<String, String>) {
        tracer.emit(AuthTraceType.LIFECYCLE, fields = listOf("cb" to callback, "inst" to instance.toString()) + extra)
    }

    private fun requestAuthentication() {
        viewModel.requestAuthentication(authenticator)
        render()
    }

    // 대기 단계에서만 인증 버튼을 켠다. 정리 중·정지는 인증 중과 따로 표시한다.
    private fun render() {
        val state = viewModel.state
        val phase = viewModel.requestPhase
        activeIndicator.isEnabled = state is ActivationState.Active
        authenticateButton.isEnabled = phase == AuthRequestPhase.Idle
        remainingText.text = getString(R.string.check_remaining_seconds, (viewModel.remainingMillis() + 999) / 1000)
        statusText.text = when {
            phase is AuthRequestPhase.Stalled -> getString(R.string.check_status_stalled)
            phase is AuthRequestPhase.Draining -> getString(R.string.check_status_draining)
            state is ActivationState.Active -> getString(R.string.check_status_active, state.method.name)
            phase is AuthRequestPhase.Authenticating -> getString(R.string.check_status_authenticating)
            else -> getString(inactiveMessage((state as ActivationState.Inactive).reason))
        }
    }

    private fun inactiveMessage(reason: InactiveReason): Int = when (reason) {
        InactiveReason.NOT_AUTHENTICATED -> R.string.check_status_not_authenticated
        InactiveReason.EXPIRED -> R.string.check_status_expired
        InactiveReason.AUTH_FAILED -> R.string.check_status_failed
        InactiveReason.AUTH_CANCELED -> R.string.check_status_canceled
        InactiveReason.LEFT_APP -> R.string.check_status_left_app
        InactiveReason.NO_RESPONSE -> R.string.check_status_no_response
        InactiveReason.SETUP_REQUIRED -> R.string.check_status_setup_required
        InactiveReason.UNAVAILABLE -> R.string.check_status_unavailable
    }

    // debug 점검 전용 ViewModel 생성. 응답 대기 시간을 이 화면에서만 주입한다.
    private object ExperimentViewModelFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VirtualCardActivationViewModel(SystemClock::elapsedRealtime, RESPONSE_TIMEOUTS, DebugAuthTrace.tracer) as T
        }
    }

    private companion object {
        const val TICK_MILLIS = 250L

        // 제안 기본값을 그대로 쓴다. 점검에서 이 값을 관측해 조정한다.
        val RESPONSE_TIMEOUTS = AuthResponseTimeouts()
    }
}
