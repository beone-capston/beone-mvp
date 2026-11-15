// TEMP-WEBVIEW-FIXTURE 페이지 이동 뒤에도 실행 ID로 관측 값을 추적하는 세션 저장소.
const RUN_KEY = 'fixture.run'
const CASE_KEY = 'fixture.case'
const ATTEMPTS_KEY = 'fixture.attempts'
const VIOLATIONS_KEY = 'fixture.violations'

/** 세션 저장소 값을 읽는다. 값이 없거나 저장소를 쓸 수 없으면 기본값을 돌려준다. */
function read(key, fallback) {
  try {
    return sessionStorage.getItem(key) ?? fallback
  } catch {
    return fallback
  }
}

/** 세션 저장소에 값을 쓴다. 저장소를 쓸 수 없으면 오류 없이 넘어간다. */
function write(key, value) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    // 저장소를 쓸 수 없어도 서버 기록으로 추적할 수 있다.
  }
}

/** 세션 저장소의 JSON 객체를 읽는다. 해석할 수 없으면 빈 객체를 돌려준다. */
function readMap(key) {
  try {
    return JSON.parse(read(key, '{}'))
  } catch {
    return {}
  }
}

/** 현재 실행 ID를 돌려주고, 없으면 새로 만든다. */
export function getRunId() {
  const saved = read(RUN_KEY, '')
  return saved || startNewRun()
}

/** 새 실행 ID를 만들고 현재 사례를 비운다. */
export function startNewRun() {
  const runId = crypto.randomUUID()
  write(RUN_KEY, runId)
  write(CASE_KEY, '')
  return runId
}

/** 실행 중인 사례를 저장한다. 다음 페이지의 위반 기록에도 쓰인다. */
export function setCurrentCase(caseId) {
  write(CASE_KEY, caseId)
}

/** 저장된 현재 사례를 돌려준다. */
export function getCurrentCase() {
  return read(CASE_KEY, '')
}

/** 실행 ID별로 시도한 사례를 기록한다. */
export function recordAttempt(runId, caseId) {
  const attempts = readMap(ATTEMPTS_KEY)
  attempts[runId] = [...(attempts[runId] ?? []), caseId]
  write(ATTEMPTS_KEY, JSON.stringify(attempts))
}

/** 실행 ID의 시도 목록을 돌려준다. */
export function getAttempts(runId) {
  return readMap(ATTEMPTS_KEY)[runId] ?? []
}

/** 실행 ID의 CSP 위반 수를 하나 늘린다. */
export function recordViolation(runId) {
  const violations = readMap(VIOLATIONS_KEY)
  violations[runId] = (violations[runId] ?? 0) + 1
  write(VIOLATIONS_KEY, JSON.stringify(violations))
}

/** 실행 ID의 CSP 위반 수를 돌려준다. */
export function getViolationCount(runId) {
  return readMap(VIOLATIONS_KEY)[runId] ?? 0
}
