// TEMP-WEBVIEW-FIXTURE 화면과 서버가 함께 쓰는 관측 규약. 합성 값과 실행 ID만 다룬다.

export const MARKER = 'TEMP-WEBVIEW-FIXTURE'
export const SAMPLE = 'synthetic'
export const FIXTURE_PORT = 5173
export const RECEIVER_PORT = 18080
export const LOOPBACK_HOST = '127.0.0.1'
export const RECEIVER_URL = `http://localhost:${RECEIVER_PORT}/w6/receive`

export const PATHS = Object.freeze({
  same: '/w6/same',
  attempt: '/w6/attempt',
  violation: '/w6/csp-violation',
  receive: '/w6/receive',
})

// 경로마다 받을 수 있는 사례를 고정해 다른 사례로 잘못 기록되지 않게 한다.
export const CASES_BY_PATH = Object.freeze({
  [PATHS.same]: Object.freeze(['C0', 'A']),
  [PATHS.attempt]: Object.freeze(['A', 'B']),
  [PATHS.receive]: Object.freeze(['C0', 'C1', 'C2', 'B', 'D']),
})

export const NO_CASE = 'NONE'
export const VIOLATION_CASES = Object.freeze(['A', 'B', 'C1', 'C2', 'D', NO_CASE])

const RUN_ID_PATTERN = /^[A-Za-z0-9-]{1,64}$/
const DIRECTIVE_PATTERN = /^[a-z-]{1,40}$/
const BLOCKED_ORIGIN_PATTERN = /^(https?:\/\/[A-Za-z0-9.:-]{1,100}|inline|eval|other)$/

/** 실행 ID 형식을 확인한다. */
export function isValidRunId(value) {
  return typeof value === 'string' && RUN_ID_PATTERN.test(value)
}

/** CSP 위반 지시문 이름 형식을 확인한다. */
export function isValidDirective(value) {
  return typeof value === 'string' && DIRECTIVE_PATTERN.test(value)
}

/** 차단된 주소를 줄여 쓴 값의 형식을 확인한다. */
export function isValidBlockedOrigin(value) {
  return typeof value === 'string' && BLOCKED_ORIGIN_PATTERN.test(value)
}

/** 차단된 주소에서 경로와 쿼리를 버리고 origin만 남긴다. */
export function summarizeBlockedUri(blockedUri) {
  if (blockedUri === 'inline' || blockedUri === 'eval') return blockedUri
  try {
    const origin = new URL(blockedUri).origin
    return isValidBlockedOrigin(origin) ? origin : 'other'
  } catch {
    return 'other'
  }
}

/** 폼 전송 본문을 만든다. */
export function encodeForm(fields) {
  return new URLSearchParams(fields).toString()
}
