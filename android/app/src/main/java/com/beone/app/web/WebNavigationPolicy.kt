package com.beone.app.web

/**
 * WebView 안에서 열어도 되는 주소를 판정한다. 신뢰 origin은 빌드 설정값에서 받으며,
 * release의 로딩 방식·origin이 정해지기 전까지 release에는 값이 없다.
 */
class WebNavigationPolicy(startUrl: String) {

    /** 설정이 없거나 잘못됐으면 null이며, 이때는 어떤 주소도 허용하지 않는다. */
    val trustedOrigin: WebOrigin? = startUrl.takeIf { it.isNotBlank() }?.let(WebOrigin::parse)

    /** 신뢰 origin을 얻은 경우에만 값이 있는 시작 주소. */
    val startUrl: String? = startUrl.trim().takeIf { trustedOrigin != null }

    /** 주소의 origin이 신뢰 origin과 같은지 판정한다. */
    fun isAllowed(url: String): Boolean {
        val trusted = trustedOrigin ?: return false
        return WebOrigin.parse(url) == trusted
    }

    /**
     * 메인 프레임 문서 요청을 네이티브에서 막아야 하는지 판정한다.
     * 하위 리소스와 iframe 요청은 판정하지 않으므로 다른 origin의 API 호출을 막지 않는다.
     */
    fun shouldBlockRequest(url: String, isForMainFrame: Boolean): Boolean = isForMainFrame && !isAllowed(url)
}
