package com.beone.app.bridge

import com.beone.app.BuildConfig
import com.beone.app.web.WebNavigationPolicy
import com.beone.app.web.WebOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** release 시작 주소와 탐색·브리지 신뢰 origin(브리지 계약 v1, D-25 2026-11-16 보충). */
class ReleaseBridgeConfigTest {

    private val policy = WebNavigationPolicy(BuildConfig.WEB_START_URL)

    @Test
    fun releaseStartsAtServerRoot() {
        assertEquals("https://beonepay.site/", BuildConfig.WEB_START_URL)
        assertFalse(BuildConfig.DEBUG)
    }

    @Test
    fun releaseTrustsExactlyOneHttpsOrigin() {
        assertEquals(WebOrigin("https", "beonepay.site", 443), policy.trustedOrigin)
        assertEquals(setOf(WebOrigin("https", "beonepay.site", 443)), BridgeOrigins.trusted(policy))
        assertEquals(setOf("https://beonepay.site"), BridgeOrigins.rules(policy))
    }

    @Test
    fun releaseListHasNoDebugCleartextWwwOrWildcard() {
        val rules = BridgeOrigins.rules(policy) + BuildConfig.WEB_START_URL
        rules.forEach { rule ->
            listOf("localhost", "127.0.0.1", "10.0.2.2", ":5173", "http://", "www.", "*").forEach { forbidden ->
                assertFalse("$rule contains $forbidden", rule.contains(forbidden))
            }
        }
    }

    @Test
    fun releaseNavigationRejectsLookalikeOrigins() {
        listOf(
            "https://www.beonepay.site/",
            "http://beonepay.site/",
            "https://beonepay.site:8443/",
            "https://sub.beonepay.site/",
            "http://localhost:5173/",
        ).forEach { assertFalse(it, policy.isAllowed(it)) }
    }
}
