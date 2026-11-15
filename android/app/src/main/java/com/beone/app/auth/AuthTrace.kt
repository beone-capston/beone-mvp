package com.beone.app.auth

// 인증 요청 단계 전이를 관측용으로 기록하는 경계. 기록은 동작에 영향을 주지 않아야 한다.
// main에는 Android 의존 없는 인터페이스와 기본 무동작 구현만 두고, logcat 출력 구현은 debug 소스셋에 둔다.
fun interface AuthTraceSink {

    fun record(event: AuthTraceEvent)

    companion object {
        // 기본값. 아무것도 기록하지 않는다(release 경로는 이 구현만 쓴다).
        val None: AuthTraceSink = AuthTraceSink { }
    }
}

// 관측 사건 한 건. 정수 코드·enum 이름·단계 이름만 담고 콜백 객체나 오류 문자열은 담지 않는다.
// requestId는 앱 내부 순번이며, 플랫폼 콜백이 그 요청의 것이라는 증거가 아니다(관측용 상관관계 표식).
data class AuthTraceEvent(
    val atMillis: Long,
    val seq: Long,
    val type: AuthTraceType,
    val from: String,
    val to: String,
    val requestId: Long?,
    val fields: List<Pair<String, String>>,
) {
    // 한 줄 형식: t=<ms> seq=<n> ev=<사건> from=<단계> to=<단계> req=<순번> <키=값>...
    fun toLine(): String = buildString {
        append("t=").append(atMillis)
        append(" seq=").append(seq)
        append(" ev=").append(type.name)
        append(" from=").append(from)
        append(" to=").append(to)
        append(" req=").append(requestId ?: "-")
        fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
    }
}

enum class AuthTraceType {
    LIFECYCLE,
    REQUEST_ATTEMPT,
    REQUEST_REJECTED,
    AVAILABILITY_BLOCKED,
    BEGIN,
    BEGIN_REJECTED,
    NOT_SENT,
    DISPATCH,
    WITHDRAW_UNSENT,
    CALLBACK,
    RESULT,
    APP_LEFT,
    CANCEL_REQUESTED,
    TIMEOUT,
    ACTIVATION,
}

// 사건에 시각·순번을 붙여 sink로 넘긴다. 메인 스레드에서만 쓰므로 순번 동기화는 하지 않는다.
class AuthTracer(
    private val elapsedRealtimeMillis: () -> Long,
    private val sink: AuthTraceSink,
) {

    private var lastSeq = 0L

    // 기록 실패가 인증 동작을 바꾸거나 앱을 중단시키지 않도록 예외를 삼킨다.
    // 호출 측 상태 전이는 이 호출 전후로 이미 끝나 있으므로 삼켜도 상태가 어긋나지 않는다.
    fun emit(
        type: AuthTraceType,
        from: AuthRequestPhase? = null,
        to: AuthRequestPhase? = null,
        requestId: Long? = null,
        fields: List<Pair<String, String>> = emptyList(),
    ) {
        if (sink === AuthTraceSink.None) return
        try {
            sink.record(
                AuthTraceEvent(
                    atMillis = elapsedRealtimeMillis(),
                    seq = ++lastSeq,
                    type = type,
                    from = from?.traceName() ?: "-",
                    to = to?.traceName() ?: "-",
                    requestId = requestId,
                    fields = fields,
                ),
            )
        } catch (e: Exception) {
            // 관측 실패는 무시한다.
        }
    }

    companion object {
        val NONE = AuthTracer({ 0L }, AuthTraceSink.None)
    }
}

fun AuthRequestPhase.traceName(): String = when (this) {
    AuthRequestPhase.Idle -> "IDLE"
    is AuthRequestPhase.Authenticating -> "AUTHENTICATING"
    is AuthRequestPhase.Draining -> "DRAINING"
    is AuthRequestPhase.Stalled -> if (cancelRequested) "STALLED_CANCEL_REQUESTED" else "STALLED"
}

val AuthRequestPhase.traceRequestId: Long?
    get() = when (this) {
        AuthRequestPhase.Idle -> null
        is AuthRequestPhase.Authenticating -> requestId
        is AuthRequestPhase.Draining -> requestId
        is AuthRequestPhase.Stalled -> requestId
    }

// 결과 종류와 정수 코드·인증 수단 이름만 꺼낸다.
fun AuthOutcome.traceFields(): List<Pair<String, String>> = when (this) {
    is AuthOutcome.Succeeded -> listOf("kind" to "SUCCEEDED", "method" to method.name)
    AuthOutcome.Mismatch -> listOf("kind" to "MISMATCH")
    is AuthOutcome.Failed -> listOf("kind" to "FAILED", "code" to errorCode.toString())
    is AuthOutcome.Canceled -> listOf("kind" to "CANCELED", "code" to errorCode.toString())
    AuthOutcome.SetupRequired -> listOf("kind" to "SETUP_REQUIRED")
    is AuthOutcome.Unavailable -> listOf("kind" to "UNAVAILABLE", "code" to errorCode.toString())
}
