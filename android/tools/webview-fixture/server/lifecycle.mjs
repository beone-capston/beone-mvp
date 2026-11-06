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

/**
 * JSONL 기록기를 연다.
 * 스트림 오류는 처음 한 번만 onError 구독자에게 알리고, 구독 전에 난 오류는 구독 시점에 바로 전달한다.
 * 오류 뒤나 닫기 시작 뒤의 write는 버린다. close의 done은 정상 종료·오류 어느 쪽이든 한 번만 부른다.
 * write 성공은 디스크 기록 완료를 뜻하지 않는다.
 */
export function openJsonlLog(logPath, createStream = createWriteStream) {
  const stream = createStream(logPath, { flags: 'a', mode: 0o600 })
  const listeners = []
  let failure = null
  let finishClose = null

  stream.on('error', (error) => {
    if (failure === null) {
      failure = error
      for (const listener of listeners) listener(error)
    }
    if (finishClose) finishClose()
  })

  return {
    write: (entry) => {
      if (failure !== null || finishClose) return
      stream.write(`${JSON.stringify(entry)}\n`)
    },
    onError: (listener) => {
      if (failure !== null) listener(failure)
      else listeners.push(listener)
    },
    close: (done) => {
      if (finishClose) return
      let called = false
      finishClose = () => {
        if (called) return
        called = true
        done()
      }
      if (failure !== null) {
        // 실패한 스트림은 끝내기를 기다리지 않고 버린다.
        stream.destroy()
        finishClose()
        return
      }
      stream.end(() => finishClose())
    },
  }
}

/**
 * loopback에서 서버를 시작하고 자동 종료와 신호 종료를 건다.
 * 기록 실패는 그 회차의 관측 실패로 보고 서버를 멈춘 뒤 종료 코드 1을 남긴다. 정상 종료 경로는 종료 코드를 바꾸지 않는다.
 */
export function startLoopbackServer({ name, port, handler, logSink, durationMs = AUTO_STOP_MS, deps = DEFAULT_DEPS }) {
  const server = deps.createServer(handler)
  server.requestTimeout = REQUEST_TIMEOUT_MS
  let stopped = false
  let timer = null
  let logClosed = false
  let logFailed = false

  const closeLog = () => {
    if (logClosed) return
    logClosed = true
    logSink.close(() => {})
  }

  /** 타이머를 해제하고 새 연결 수락을 멈춘 뒤 기존 연결과 기록을 닫는다. 두 번째 호출부터는 아무것도 하지 않는다. */
  const shutdown = () => {
    if (stopped) return
    stopped = true
    if (timer !== null) deps.clearTimer(timer)
    timer = null
    server.close(() => closeLog())
    server.closeAllConnections()
  }

  const stop = (reason) => {
    if (stopped) return
    deps.print(`[${name}] 종료: ${reason}`)
    shutdown()
  }

  /** 기록 실패를 한 번만 보고하고 서버를 멈춘다. 실패한 기록에는 다시 쓰지 않는다. */
  const failOnLogError = (error) => {
    if (logFailed) return
    logFailed = true
    deps.setExitCode(1)
    deps.printError(`[${name}] 기록 실패: ${error?.code ?? 'UNKNOWN'}. 서버를 멈춥니다. 이 회차의 결과는 증거로 쓰지 않습니다.`)
    shutdown()
  }

  server.on('error', (error) => {
    // 포트 충돌 시 다른 프로세스를 건드리지 않고 실패한다.
    deps.printError(`[${name}] 시작 실패: ${error.code ?? 'UNKNOWN'} (${LOOPBACK_HOST}:${port})`)
    deps.setExitCode(1)
    stopped = true
    if (timer !== null) deps.clearTimer(timer)
    timer = null
    closeLog()
  })

  deps.onSignal('SIGINT', () => stop('SIGINT'))
  deps.onSignal('SIGTERM', () => stop('SIGTERM'))
  logSink.onError(failOnLogError)

  // 구독 시점에 이미 기록이 실패했다면 포트를 열지 않는다.
  if (!stopped) {
    server.listen(port, LOOPBACK_HOST, () => {
      if (stopped) {
        // 시작 완료 전에 멈췄다면 늦게 열린 포트를 바로 닫고 타이머·시작 안내를 만들지 않는다.
        server.close(() => closeLog())
        server.closeAllConnections()
        return
      }
      timer = deps.setTimer(() => stop('자동 종료 시간 도달'), durationMs)
      deps.print(`[${name}] 시작: http://${LOOPBACK_HOST}:${port} 자동 종료 ${Math.round(durationMs / 60000)}분 뒤`)
    })
  }

  return { server, stop }
}
