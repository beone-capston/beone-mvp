package com.beone.app.web

import com.beone.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DebugWebConfigTest {

    @Test
    fun debugLoadsLocalDevServer() {
        val origin = WebNavigationPolicy(BuildConfig.WEB_START_URL).trustedOrigin
        assertNotNull(BuildConfig.WEB_START_URL, origin)

        // 기본 주소 검사는 beone.devServerUrl을 바꾸지 않은 빌드에서만 한다.
        if (!BuildConfig.DEV_SERVER_OVERRIDDEN) {
            assertEquals(WebOrigin("http", "localhost", 5173), origin)
        }
    }
}
