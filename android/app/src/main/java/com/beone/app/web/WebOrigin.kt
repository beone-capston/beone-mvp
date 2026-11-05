package com.beone.app.web

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** 스킴·호스트·포트로 이루어진 웹 origin. 기본 포트는 명시 여부와 관계없이 같게 본다. */
data class WebOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
) {

    companion object {
        private val DEFAULT_PORTS = mapOf("https" to 443, "http" to 80)
        private val VALID_PORTS = 0..65535

        /** http·https 절대 주소만 받는다. 형식이 잘못됐거나, 다른 스킴이거나, 포트가 유효 범위 밖이면 null. */
        fun parse(url: String): WebOrigin? {
            val uri = try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val defaultPort = DEFAULT_PORTS[scheme] ?: return null
            val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
            val port = if (uri.port == -1) defaultPort else uri.port
            if (port !in VALID_PORTS) return null
            return WebOrigin(scheme, host, port)
        }
    }
}
