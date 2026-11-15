// TEMP-WEBVIEW-FIXTURE CSP 위반을 실행 ID와 함께 세션과 서버 기록에 남긴다.
import {
  NO_CASE,
  PATHS,
  VIOLATION_CASES,
  encodeForm,
  isValidDirective,
  summarizeBlockedUri,
} from '../shared/protocol.js'
import { getCurrentCase, getRunId, recordViolation } from './runState.js'

/** 페이지의 CSP 위반 이벤트를 기록하도록 등록한다. 위반 수는 차단 주체를 말해 주지 않는다. */
export function installViolationRecorder() {
  document.addEventListener('securitypolicyviolation', (event) => {
    const run = getRunId()
    recordViolation(run)
    const saved = getCurrentCase()
    const directive = event.effectiveDirective
    fetch(PATHS.violation, {
      method: 'POST',
      keepalive: true,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: encodeForm({
        run,
        case: VIOLATION_CASES.includes(saved) ? saved : NO_CASE,
        directive: isValidDirective(directive) ? directive : 'unknown',
        blockedOrigin: summarizeBlockedUri(event.blockedURI),
      }),
    }).catch(() => {})
  })
}
