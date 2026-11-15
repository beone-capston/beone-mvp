package com.beone.app.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricResultMapperTest {

    @Test
    fun allowedAuthenticatorsAreStrongBiometricOrDeviceCredential() {
        assertEquals(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricDeviceAuthenticator.ALLOWED_AUTHENTICATORS,
        )
    }

    @Test
    fun availabilitySuccessAllowsAuthentication() {
        assertNull(BiometricResultMapper.availabilityOutcome(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun unknownStatusStillTriesAuthentication() {
        assertNull(BiometricResultMapper.availabilityOutcome(BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
    }

    @Test
    fun noBiometricAndNoScreenLockRequiresSetup() {
        assertEquals(
            AuthOutcome.SetupRequired,
            BiometricResultMapper.availabilityOutcome(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun otherAvailabilityStatusesAreUnavailable() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        ).forEach { status ->
            assertEquals(AuthOutcome.Unavailable(status), BiometricResultMapper.availabilityOutcome(status))
        }
    }

    @Test
    fun cancelErrorsMapToCanceled() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { code ->
            assertEquals(AuthOutcome.Canceled(code), BiometricResultMapper.errorOutcome(code))
        }
    }

    @Test
    fun missingCredentialErrorsRequireSetup() {
        listOf(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, BiometricPrompt.ERROR_NO_BIOMETRICS).forEach { code ->
            assertEquals(AuthOutcome.SetupRequired, BiometricResultMapper.errorOutcome(code))
        }
    }

    @Test
    fun hardwareErrorsAreUnavailable() {
        listOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        ).forEach { code ->
            assertEquals(AuthOutcome.Unavailable(code), BiometricResultMapper.errorOutcome(code))
        }
    }

    @Test
    fun lockoutTimeoutAndProcessingErrorsAreFailures() {
        listOf(
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_VENDOR,
        ).forEach { code ->
            assertEquals(AuthOutcome.Failed(code), BiometricResultMapper.errorOutcome(code))
        }
    }

    @Test
    fun authenticationTypeMapsToMethod() {
        assertEquals(
            AuthMethod.BIOMETRIC,
            BiometricResultMapper.authMethod(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC),
        )
        assertEquals(
            AuthMethod.DEVICE_CREDENTIAL,
            BiometricResultMapper.authMethod(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL),
        )
        assertEquals(
            AuthMethod.UNKNOWN,
            BiometricResultMapper.authMethod(BiometricPrompt.AUTHENTICATION_RESULT_TYPE_UNKNOWN),
        )
    }
}
