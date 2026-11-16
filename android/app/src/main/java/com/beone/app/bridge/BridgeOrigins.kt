package com.beone.app.bridge

import com.beone.app.web.WebNavigationPolicy
import com.beone.app.web.WebOrigin

/**
 * 브리지 신뢰 origin. 탐색 정책의 신뢰 origin 하나를 그대로 쓰므로 빌드 변형별 목록이 따로 생기고 전역 목록은 없다.
 * debug는 개발 서버 origin, release는 `https://beonepay.site`다.
 */
object BridgeOrigins {

    /** 핸들러가 다시 확인할 신뢰 origin. 설정이 없으면 비어 있어 어떤 메시지도 처리하지 않는다. */
    fun trusted(policy: WebNavigationPolicy): Set<WebOrigin> = setOfNotNull(policy.trustedOrigin)

    /** `addWebMessageListener`에 넘길 허용 origin 규칙. */
    fun rules(policy: WebNavigationPolicy): Set<String> = trusted(policy).map(::rule).toSet()

    /** 와일드카드 없이 `스킴://호스트[:포트]`로 정확히 적는다. 기본 포트(https 443, http 80)는 적지 않는다. */
    fun rule(origin: WebOrigin): String {
        val defaultPort = if (origin.scheme == "https") 443 else 80
        val port = if (origin.port == defaultPort) "" else ":${origin.port}"
        return "${origin.scheme}://${origin.host}$port"
    }
}
