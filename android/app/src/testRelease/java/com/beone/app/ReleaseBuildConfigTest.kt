package com.beone.app

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * release 빌드 설정을 고정한다(MVP Android 갭 AG-09, U1).
 * MainActivity는 BuildConfig.DEBUG가 true일 때만 WebView 원격 디버깅을 켜므로, 이 값이 false면 release에서 원격 디버깅이 꺼진다.
 * 빌드 산출물(APK)이나 기기 동작은 확인하지 않는다.
 */
class ReleaseBuildConfigTest {

    @Test
    fun releaseBuildIsNotDebug() {
        assertFalse(BuildConfig.DEBUG)
    }
}
