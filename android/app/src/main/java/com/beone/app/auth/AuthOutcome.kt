package com.beone.app.auth

// 기기 인증 요청의 결과. 생체 데이터·PIN은 담지 않는다.
sealed interface AuthOutcome {

    // 인증 창이 닫히는 최종 결과인지 여부
    val isTerminal: Boolean get() = true

    data class Succeeded(val method: AuthMethod) : AuthOutcome

    // 지문 불일치 한 번. 인증 창은 열린 채로 다시 시도할 수 있다.
    data object Mismatch : AuthOutcome {
        override val isTerminal: Boolean get() = false
    }

    // 잠김, 시간 초과 등으로 인증 창이 닫힌 실패
    data class Failed(val errorCode: Int) : AuthOutcome

    // 사용자 취소 또는 시스템 취소
    data class Canceled(val errorCode: Int) : AuthOutcome

    // 생체·화면 잠금이 모두 없어 기기 인증 설정이 필요함
    data object SetupRequired : AuthOutcome

    // 기기가 이 인증 방식을 지원하지 않거나 일시적으로 쓸 수 없음
    data class Unavailable(val errorCode: Int) : AuthOutcome
}

enum class AuthMethod {
    BIOMETRIC,
    DEVICE_CREDENTIAL,
    UNKNOWN,
}
