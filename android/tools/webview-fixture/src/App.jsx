// TEMP-WEBVIEW-FIXTURE W1·W2·W5(debug)·W6 관측 전용 화면. 브리지와 업무 기능을 넣지 않는다.
import { useEffect, useState } from 'react'
import { MARKER, PATHS, RECEIVER_URL, SAMPLE, encodeForm } from '../shared/protocol.js'
import {
  getAttempts,
  getRunId,
  getViolationCount,
  recordAttempt,
  setCurrentCase,
  startNewRun,
} from './runState.js'

const FORM_HEADERS = { 'Content-Type': 'application/x-www-form-urlencoded' }

const edgeStyle = {
  position: 'fixed',
  left: 0,
  right: 0,
  padding: '4px 8px',
  background: '#7a1f1f',
  color: '#fff',
  fontWeight: 700,
  textAlign: 'center',
}

function readLocation() {
  return { href: location.href, historyLength: history.length }
}

/** 합성 값을 담은 폼 POST. 제출 직전에 같은 origin으로 시도 기록을 보낸다. */
function SyntheticForm({ run, caseId, action, label, onAttempt }) {
  /** 기본 폼 제출 전에 현재 사례와 시도를 세션에 남기고, 같은 origin으로 시도 기록을 보낸다. 시도 기록의 도착은 보장하지 않는다. */
  const handleSubmit = () => {
    setCurrentCase(caseId)
    recordAttempt(run, caseId)
    onAttempt()
    fetch(PATHS.attempt, {
      method: 'POST',
      keepalive: true,
      headers: FORM_HEADERS,
      body: encodeForm({ run, case: caseId, sample: SAMPLE }),
    }).catch(() => {})
  }

  return (
    <form method="post" action={action} onSubmit={handleSubmit}>
      <input type="hidden" name="run" value={run} />
      <input type="hidden" name="case" value={caseId} />
      <input type="hidden" name="sample" value={SAMPLE} />
      <button type="submit">{label}</button>
    </form>
  )
}

/** 관측 항목별 조작과 현재 값을 표시한다. */
export function App() {
  const [run, setRun] = useState(getRunId)
  const [where, setWhere] = useState(readLocation)
  const [counter, setCounter] = useState(0)
  const [results, setResults] = useState({})
  const [, setRevision] = useState(0)
  const isPageTwo = location.pathname === '/page-2'

  useEffect(() => {
    const update = () => setWhere(readLocation())
    window.addEventListener('resize', update)
    window.addEventListener('popstate', update)
    return () => {
      window.removeEventListener('resize', update)
      window.removeEventListener('popstate', update)
    }
  }, [])

  const refresh = () => setRevision((value) => value + 1)

  /** 수신기로 합성 하위 요청을 보낸다. 응답이 불투명하므로 화면 문구가 아니라 수신기 기록으로 판정한다. */
  const sendSubRequest = (caseId) => {
    setCurrentCase(caseId)
    recordAttempt(run, caseId)
    refresh()
    fetch(RECEIVER_URL, {
      method: 'POST',
      mode: 'no-cors',
      headers: FORM_HEADERS,
      body: encodeForm({ run, case: caseId, sample: SAMPLE }),
    }).then(
      () => setResults((value) => ({ ...value, [caseId]: '요청 완료(불투명 응답). 수신기 기록으로 판정' })),
      () => setResults((value) => ({ ...value, [caseId]: '요청 오류. 수신기 기록을 확인' })),
    )
  }

  return (
    <main style={{ padding: '48px 16px', fontFamily: 'sans-serif', lineHeight: 1.5 }}>
      <div style={{ ...edgeStyle, top: 0 }}>TOP-EDGE {MARKER}</div>
      <div style={{ ...edgeStyle, bottom: 0 }}>BOTTOM-EDGE {MARKER}</div>

      <h1 style={{ fontSize: 20 }}>{MARKER}</h1>
      <p>임시 관측 화면입니다. 실제 프런트 화면이 아니며, 이 화면의 결과를 실제 연동 결과로 쓰지 않습니다.</p>

      <section>
        <h2 style={{ fontSize: 16 }}>W1 화면 표시</h2>
        <p>위 TOP-EDGE와 아래 BOTTOM-EDGE가 시스템 바에 가려지는지 봅니다. 현재 페이지: {isPageTwo ? 'page-2' : '첫 페이지'}</p>
      </section>

      <section>
        <h2 style={{ fontSize: 16 }}>W2 주소와 웹 기록</h2>
        <p>주소: <code>{where.href}</code></p>
        <p>history.length: {where.historyLength}</p>
        <p>{isPageTwo ? <a href="/">첫 페이지로 이동</a> : <a href="/page-2">page-2로 이동</a>}</p>
        <p>
          기록만 하는 JS 상태(판정 제외): 카운터 {counter}{' '}
          <button
            type="button"
            onClick={() => {
              window.__w2 = Date.now()
              setCounter((value) => value + 1)
            }}
          >
            값 넣기
          </button>
        </p>
      </section>

      <section>
        <h2 style={{ fontSize: 16 }}>W6 합성 POST</h2>
        <p>실행 ID: <code>{run}</code></p>
        <p>이 실행에서 시도한 사례: {getAttempts(run).join(', ') || '없음'}</p>
        <p>CSP 위반 수(이 실행): {getViolationCount(run)}</p>
        <p>
          <button
            type="button"
            onClick={() => {
              setRun(startNewRun())
              setResults({})
            }}
          >
            새 실행 ID
          </button>
        </p>
        <p>
          <button type="button" onClick={() => sendSubRequest('C1')}>C1 경로 대조(하위 요청)</button>{' '}
          {results.C1}
        </p>
        <SyntheticForm run={run} caseId="A" action={PATHS.same} label="A 같은 origin 폼 POST" onAttempt={refresh} />
        <SyntheticForm run={run} caseId="B" action={RECEIVER_URL} label="B 다른 origin 메인 프레임 폼 POST" onAttempt={refresh} />
        <p>
          <button type="button" onClick={() => sendSubRequest('C2')}>C2 경로 대조(B 뒤 다시 실행)</button>{' '}
          {results.C2}
        </p>
        <p>
          <button type="button" onClick={() => sendSubRequest('D')}>D 하위 요청</button> {results.D}
        </p>
        <ul>
          <li>수신 기록 없음은 네트워크 전송 0의 증명이 아닙니다.</li>
          <li>안내 문구나 위반 수 0만으로 차단 주체를 단정하지 않습니다.</li>
        </ul>
      </section>
    </main>
  )
}
