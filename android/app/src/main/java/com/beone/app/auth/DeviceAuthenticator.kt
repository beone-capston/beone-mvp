package com.beone.app.auth

// 기기 인증 창을 띄우는 경계. 실제 구현과 테스트용 대체 구현을 분리한다.
interface DeviceAuthenticator {

    // 인증을 시작할 수 없으면 그 이유를, 시작할 수 있으면 null을 돌려준다.
    fun availability(): AuthOutcome?

    // 인증 요청을 라이브러리에 전달한다. 결과는 생성 시 받은 콜백으로 전달된다.
    // 반환값은 전달 여부일 뿐이며, 실제 인증 창이 표시됐는지는 공개 API로 알 수 없다.
    fun authenticate(): AuthRequestDispatch

    // 진행 중인 인증의 취소를 라이브러리에 요청한다. 취소 완료나 이후 결과 도착을 보장하지 않는다.
    // 이후 도착한 결과를 쓸지는 호출 측 상태(AuthRequestGate)가 정한다.
    fun cancel()
}

enum class AuthRequestDispatch {
    // 라이브러리 authenticate를 호출했다. 창 표시·인증 시작을 뜻하지 않는다.
    SENT_TO_LIBRARY,

    // 호출 전 조건(화면 상태 저장됨·종료 중 등) 때문에 라이브러리를 호출하지 않았다. 이 요청의 결과는 오지 않는다.
    NOT_SENT,
}
