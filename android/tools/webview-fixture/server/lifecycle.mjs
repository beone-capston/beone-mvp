// TEMP-WEBVIEW-FIXTURE 서버 시작·자동 종료·기록 파일. 호출하기 전에는 포트·타이머·파일을 건드리지 않는다.
import { createWriteStream } from 'node:fs'
import { createServer } from 'node:http'
import path from 'node:path'
import { LOOPBACK_HOST } from '../shared/protocol.js'

export const AUTO_STOP_MS = 30 * 60 * 1000
const REQUEST_TIMEOUT_MS = 10_000

const DEFAULT_DEPS = {
  createServer,
  setTimer: setTimeout,
  clearTimer: clearTimeout,
  onSignal: (signal, listener) => process.on(signal, listener),
  print: (message) => console.log(message),
  printError: (message) => console.error(message),
  setExitCode: (code) => {
    process.exitCode = code
  },
}

/** 명령줄에서 기록 경로를 얻는다. 절대 경로이고 금지 폴더 밖이어야 한다. */
export function parseLogPath(argv, forbiddenRoot) {
  const index = argv.indexOf('--log')
  const value = index >= 0 ? argv[index + 1] : undefined
  if (!value || !path.isAbsolute(value)) return null
  const relative = path.relative(forbiddenRoot, path.resolve(value))
  const inside = relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
  return inside ? null : path.resolve(value)
}

/** JSONL 기록기를 연다. */
export function openJsonlLog(logPath, createStream = createWriteStream) {
  const stream = createStream(logPath, { flags: 'a', mode: 0o600 })
  return {
    write: (entry) => stream.write(`${JSON.stringify(entry)}\n`),
    close: (done) => stream.end(done),
  }
}

/** loopback에서 서버를 시작하고 자동 종료와 신호 종료를 건다. */
export function startLoopbackServer({ name, port, handler, logSink, durationMs = AUTO_STOP_MS, deps = DEFAULT_DEPS }) {
  const server = deps.createServer(handler)
  server.requestTimeout = REQUEST_TIMEOUT_MS
  let stopped = false
  let timer = null

  const stop = (reason) => {
    if (stopped) return
    stopped = true
    if (timer !== null) deps.clearTimer(timer)
    deps.print(`[${name}] 종료: ${reason}`)
    server.close(() => logSink.close(() => {}))
    server.closeAllConnections()
  }

  server.on('error', (error) => {
    // 포트 충돌 시 다른 프로세스를 건드리지 않고 실패한다.
    deps.printError(`[${name}] 시작 실패: ${error.code ?? 'UNKNOWN'} (${LOOPBACK_HOST}:${port})`)
    deps.setExitCode(1)
    stopped = true
    if (timer !== null) deps.clearTimer(timer)
    logSink.close(() => {})
  })

  deps.onSignal('SIGINT', () => stop('SIGINT'))
  deps.onSignal('SIGTERM', () => stop('SIGTERM'))

  server.listen(port, LOOPBACK_HOST, () => {
    if (stopped) return
    timer = deps.setTimer(() => stop('자동 종료 시간 도달'), durationMs)
    deps.print(`[${name}] 시작: http://${LOOPBACK_HOST}:${port} 자동 종료 ${Math.round(durationMs / 60000)}분 뒤`)
  })

  return { server, stop }
}
