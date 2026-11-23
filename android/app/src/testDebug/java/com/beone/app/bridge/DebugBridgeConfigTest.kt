package com.beone.app.bridge

import com.beone.app.BuildConfig
import com.beone.app.web.WebNavigationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** debug 브리지 허용 origin은 기존 방식대로 개발 서버 주소의 origin 하나다. release origin은 넣지 않는다. */
class DebugBridgeConfigTest {

    private val rules = BridgeOrigins.rules(WebNavigationPolicy(BuildConfig.WEB_START_URL))

    @Test
    fun debugBridgeTrustsDevServerOnly() {
        assertEquals(1, rules.size)
        // 기본 주소 검사는 beone.devServerUrl을 바꾸지 않은 빌드에서만 한다.
        if (!BuildConfig.DEV_SERVER_OVERRIDDEN) {
            assertEquals(setOf("http://localhost:5173"), rules)
        }
    }

    @Test
    fun debugListHasNoReleaseOriginOrWildcard() {
        rules.forEach { rule ->
            assertFalse(rule, rule.contains("*"))
            if (!BuildConfig.DEV_SERVER_OVERRIDDEN) assertFalse(rule, rule.contains("beonepay.site"))
        }
    }
}
