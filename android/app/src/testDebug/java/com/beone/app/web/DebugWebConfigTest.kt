package com.beone.app.web

import com.beone.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** debug 빌드의 개발 서버 주소 설정을 고정한다. 실제 연결은 확인하지 않는다. */
class DebugWebConfigTest {

    /** 기본 빌드는 로컬 개발 서버 origin을 쓴다. */
    @Test
    fun debugLoadsLocalDevServer() {
        val origin = WebNavigationPolicy(BuildConfig.WEB_START_URL).trustedOrigin
        assertNotNull(BuildConfig.WEB_START_URL, origin)

        // 기본 주소 검사는 beone.devServerUrl을 바꾸지 않은 빌드에서만 한다.
        if (!BuildConfig.DEV_SERVER_OVERRIDDEN) {
            assertEquals(WebOrigin("http", "localhost", 5173), origin)
        }
    }

    /** 요청한 주소가 BuildConfig에 그대로 들어간다. */
    @Test
    fun debugUsesRequestedDevServerUrl() {
        // 기대값은 BuildConfig가 아니라 Gradle이 넘긴 시스템 속성과 고정된 기본 주소에서 만든다.
        val requested = System.getProperty("beone.test.requestedDevServerUrl")

        assertEquals(requested ?: "http://localhost:5173/", BuildConfig.WEB_START_URL)
        assertEquals(requested != null, BuildConfig.DEV_SERVER_OVERRIDDEN)
    }
}
