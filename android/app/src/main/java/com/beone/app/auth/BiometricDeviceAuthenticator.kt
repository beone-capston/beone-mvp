package com.beone.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.beone.app.R

// D-08: BIOMETRIC_STRONG | DEVICE_CREDENTIAL 단일 프롬프트. 지문을 쓸 수 없으면 같은 창에서 기기 PIN으로 진행한다.
// 화면 재생성 뒤에도 결과를 받도록 Activity의 onCreate에서 매번 새로 만든다(androidx.biometric 권장 방식).
// trace는 콜백 도착과 사전 거부 이유만 기록한다. 결과 객체·오류 문자열은 기록하지 않는다.
class BiometricDeviceAuthenticator(
    private val activity: FragmentActivity,
    private val trace: AuthTracer = AuthTracer.NONE,
    onOutcome: (AuthOutcome) -> Unit,
) : DeviceAuthenticator {

    private val biometricManager = BiometricManager.from(activity)

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                trace.emit(
                    AuthTraceType.CALLBACK,
                    fields = listOf("cb" to "SUCCEEDED", "authType" to result.authenticationType.toString()),
                )
                onOutcome(AuthOutcome.Succeeded(BiometricResultMapper.authMethod(result.authenticationType)))
            }

            override fun onAuthenticationFailed() {
                trace.emit(AuthTraceType.CALLBACK, fields = listOf("cb" to "FAILED"))
                onOutcome(AuthOutcome.Mismatch)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                trace.emit(AuthTraceType.CALLBACK, fields = listOf("cb" to "ERROR", "code" to errorCode.toString()))
                onOutcome(BiometricResultMapper.errorOutcome(errorCode))
            }
        },
    )

    private val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.device_auth_prompt_title))
        .setSubtitle(activity.getString(R.string.device_auth_prompt_subtitle))
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()

    override fun availability(): AuthOutcome? =
        BiometricResultMapper.availabilityOutcome(biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS))

    // 라이브러리가 로그만 남기고 조용히 무시하는 조건(상태 저장 뒤 호출 등)이면 호출하지 않는다.
    // 직전 인증 결과 뒤 예약만 된 내부 Fragment 제거를 먼저 실행해, 곧 제거될 Fragment에서 새 창을 띄우지 않게 한다.
    // 호출 위치는 화면 생성(onCreate)과 버튼 클릭이며, Fragment 트랜잭션 실행 중에 불리면 예외 대신 NOT_SENT로 둔다.
    override fun authenticate(): AuthRequestDispatch {
        val fragmentManager = activity.supportFragmentManager
        // 기존 조건과 같은 순서로 검사하고, 처음 걸린 조건을 거부 이유로 기록한다.
        val refusedReason = when {
            activity.isFinishing -> "FINISHING"
            activity.isDestroyed || fragmentManager.isDestroyed -> "DESTROYED"
            fragmentManager.isStateSaved -> "STATE_SAVED"
            else -> null
        }
        if (refusedReason != null) {
            traceNotSent(refusedReason)
            return AuthRequestDispatch.NOT_SENT
        }
        try {
            fragmentManager.executePendingTransactions()
        } catch (e: IllegalStateException) {
            traceNotSent("PENDING_TRANSACTIONS_FAILED")
            return AuthRequestDispatch.NOT_SENT
        }
        prompt.authenticate(promptInfo)
        return AuthRequestDispatch.SENT_TO_LIBRARY
    }

    private fun traceNotSent(reason: String) {
        trace.emit(AuthTraceType.NOT_SENT, fields = listOf("reason" to reason))
    }

    override fun cancel() {
        prompt.cancelAuthentication()
    }

    companion object {
        const val ALLOWED_AUTHENTICATORS: Int = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
