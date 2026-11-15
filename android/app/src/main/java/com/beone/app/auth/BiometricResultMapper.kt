package com.beone.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

// androidx.biometric의 상태·오류 코드를 앱의 인증 결과로 바꾼다.
object BiometricResultMapper {

    // 인증을 시작할 수 없는 상태면 그 결과를, 시작할 수 있으면 null을 돌려준다.
    fun availabilityOutcome(canAuthenticateStatus: Int): AuthOutcome? = when (canAuthenticateStatus) {
        // 상태를 알 수 없으면 인증을 시도해 보고 결과 콜백으로 판단한다(BiometricManager 문서).
        BiometricManager.BIOMETRIC_SUCCESS,
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        -> null
        // 생체와 화면 잠금이 모두 없음
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthOutcome.SetupRequired
        else -> AuthOutcome.Unavailable(canAuthenticateStatus)
    }

    fun errorOutcome(errorCode: Int): AuthOutcome = when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> AuthOutcome.Canceled(errorCode)

        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        -> AuthOutcome.SetupRequired

        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        -> AuthOutcome.Unavailable(errorCode)

        // 잠김·시간 초과·처리 불가 등은 실패로 본다.
        else -> AuthOutcome.Failed(errorCode)
    }

    fun authMethod(authenticationType: Int): AuthMethod = when (authenticationType) {
        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> AuthMethod.BIOMETRIC
        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> AuthMethod.DEVICE_CREDENTIAL
        else -> AuthMethod.UNKNOWN
    }
}
