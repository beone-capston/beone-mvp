package com.beone.app.auth

import android.os.SystemClock
import android.util.Log

// debug 빌드 전용 관측 기록. 인증 단계 전이를 logcat 한 태그로 출력한다. release 소스셋에는 이 파일이 없다.
// 화면 재생성 뒤에도 순번이 이어지도록 프로세스에 하나만 둔다.
object DebugAuthTrace {

    const val TAG = "BeoneAuthTrace"

    private val sink = AuthTraceSink { event -> Log.i(TAG, event.toLine()) }

    val tracer = AuthTracer(SystemClock::elapsedRealtime, sink)

    private var lastInstance = 0

    // 점검 화면 인스턴스 구분용 순번. 메인 스레드에서만 부른다.
    fun nextInstance(): Int = ++lastInstance
}
