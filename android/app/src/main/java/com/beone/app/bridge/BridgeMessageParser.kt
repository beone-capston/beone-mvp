package com.beone.app.bridge

import java.math.BigDecimal

/**
 * 신뢰 origin·최상위 프레임 확인을 통과한 웹 메시지를 계약의 검사 순서(크기 → JSON → `v` → `type`)대로 판정한다.
 * 먼저 걸린 단계의 결과만 돌려주며, 인증 상태에는 손대지 않는다.
 */
object BridgeMessageParser {

    /** 판정 결과. 처리할 요청이거나 거부 사유다. */
    sealed interface Result {
        data class Accepted(val request: BridgeRequest) : Result
        data class Rejected(val code: BridgeErrorCode) : Result
    }

    /** 문자열이 아닌 메시지(ArrayBuffer 등)는 null로 받아 형식 오류로 본다. */
    fun parse(message: String?): Result {
        if (message == null) return reject(BridgeErrorCode.INVALID_MESSAGE)
        // 크기: JSON을 해석하기 전에 UTF-16 코드 단위 길이로 거른다.
        if (message.length > BridgeContract.MAX_MESSAGE_LENGTH) return reject(BridgeErrorCode.INVALID_MESSAGE)

        // JSON: 최상위가 객체가 아니면 형식 오류다.
        val root = try {
            BridgeJson.parse(message)
        } catch (_: BridgeJson.MalformedJsonException) {
            return reject(BridgeErrorCode.INVALID_MESSAGE)
        }
        if (root !is Map<*, *>) return reject(BridgeErrorCode.INVALID_MESSAGE)

        // v: 없으면 형식 오류, 있지만 숫자 1이 아니면 버전 오류.
        if (!root.containsKey(KEY_VERSION)) return reject(BridgeErrorCode.INVALID_MESSAGE)
        if (!isVersionOne(root[KEY_VERSION])) return reject(BridgeErrorCode.UNSUPPORTED_VERSION)

        // type: 없거나 문자열이 아니면 형식 오류, 모르는 값이면 UNKNOWN_TYPE.
        val type = root[KEY_TYPE] as? String ?: return reject(BridgeErrorCode.INVALID_MESSAGE)
        val request = BridgeRequest.fromType(type) ?: return reject(BridgeErrorCode.UNKNOWN_TYPE)
        return Result.Accepted(request)
    }

    /** JSON 숫자 값이 1인지 본다. `1`, `1.0`, `1e0`은 같은 숫자이고, 문자열 `"1"`·`true`·`null`은 숫자가 아니다. */
    private fun isVersionOne(value: Any?): Boolean {
        val number = value as? BridgeJson.JsonNumber ?: return false
        return try {
            BigDecimal(number.literal).compareTo(BigDecimal.ONE) == 0
        } catch (_: NumberFormatException) {
            // 지수가 표현 범위를 넘는 숫자는 1일 수 없다.
            false
        } catch (_: ArithmeticException) {
            false
        }
    }

    private fun reject(code: BridgeErrorCode) = Result.Rejected(code)

    private const val KEY_VERSION = "v"
    private const val KEY_TYPE = "type"
}
