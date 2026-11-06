// TEMP-WEBVIEW-FIXTURE 단위 검사. 포트를 열지 않고 가짜 요청·응답과 가짜 서버만 쓴다.
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { mkdir, mkdtemp, readFile, rm, symlink, writeFile } from 'node:fs/promises'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { EventEmitter } from 'node:events'
import { Readable } from 'node:stream'
import { after, before, describe, it } from 'node:test'
import { fileURLToPath } from 'node:url'

// 이 파일의 어떤 검사도 실제 포트를 열지 못하게 막는다.
net.Server.prototype.listen = () => {
  throw new Error('단위 검사에서 포트를 열 수 없습니다')
}

const realCreateWriteStream = fs.createWriteStream
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const RUN = 'run-1'
const HOST = { fixture: 'localhost:5173', receiver: 'localhost:18080' }

const { createFixtureHandler, resolveStaticFile } = await import('../server/fixture-handler.mjs')
const { createReceiverHandler } = await import('../server/receiver-handler.mjs')
const { AUTO_STOP_MS, openJsonlLog, parseLogPath, startLoopbackServer } = await import('../server/lifecycle.mjs')
const { MAX_BODY_BYTES } = await import('../server/http-common.mjs')
const protocol = await import('../shared/protocol.js')

/** 소켓 없이 처리기에 넣을 가짜 요청을 만든다. 본문은 스트림으로 읽히고 길이 헤더를 함께 둔다. */
function fakeRequest({ method = 'POST', url, host, type = 'application/x-www-form-urlencoded', body = '' }) {
  const req = Readable.from(body ? [Buffer.from(body)] : [])
  req.method = method
  req.url = url
  req.headers = { host, 'content-type': type, 'content-length': String(Buffer.byteLength(body)) }
  return req
}

function fakeResponse() {
  return {
    status: null,
    headers: {},
    body: undefined,
    writeHead(status, headers) {
      this.status = status
      this.headers = headers
    },
    end(body) {
      this.body = body
    },
  }
}

async function call(handler, options) {
  const res = fakeResponse()
  await handler(fakeRequest(options), res)
  return res
}

const form = (fields) => new URLSearchParams(fields).toString()
/** 합성 POST 본문을 만든다. extra로 필드를 더하거나 덮어써 거부 사례를 만든다. */
const synthetic = (caseId, extra = {}) => form({ run: RUN, case: caseId, sample: 'synthetic', ...extra })
const hasCsp = (res) => Object.keys(res.headers).some((name) => name.toLowerCase().includes('content-security-policy'))

describe('import 부작용', () => {
  it('실행 진입점을 import해도 포트·타이머·기록을 시작하지 않는다', async () => {
    const originalTimeout = globalThis.setTimeout
    let timers = 0
    let streams = 0
    globalThis.setTimeout = (...args) => {
      timers += 1
      return originalTimeout(...args)
    }
    fs.createWriteStream = (...args) => {
      streams += 1
      return realCreateWriteStream(...args)
    }
    try {
      await import('../server/run-fixture.mjs')
      await import('../server/run-receiver.mjs')
      assert.equal(timers, 0)
      assert.equal(streams, 0)
    } finally {
      globalThis.setTimeout = originalTimeout
      fs.createWriteStream = realCreateWriteStream
    }
  })
})

describe('화면 서버 처리기', () => {
  let dist
  let outside
  let entries
  let handler

  before(async () => {
    const base = await mkdtemp(path.join(os.tmpdir(), 'fixture-test-'))
    dist = path.join(base, 'dist')
    outside = path.join(base, 'secret.txt')
    await mkdir(path.join(dist, 'assets'), { recursive: true })
    await writeFile(path.join(dist, 'index.html'), '<title>TEMP-WEBVIEW-FIXTURE</title>')
    await writeFile(path.join(dist, 'assets', 'a.js'), 'x')
    await writeFile(outside, 'secret')
    await symlink(outside, path.join(dist, 'link.txt'))
    entries = []
    handler = createFixtureHandler({ distDir: dist, log: (entry) => entries.push(entry), now: () => 0 })
  })

  after(async () => {
    await rm(path.dirname(dist), { recursive: true, force: true })
  })

  it('화면 경로는 index.html을 주고 CSP를 붙이지 않는다', async () => {
    for (const url of ['/', '/page-2']) {
      const res = await call(handler, { method: 'GET', url, host: HOST.fixture })
      assert.equal(res.status, 200)
      assert.match(String(res.body), /TEMP-WEBVIEW-FIXTURE/)
      assert.equal(hasCsp(res), false)
    }
  })

  it('GET을 경로별로 기록해 횟수를 셀 수 있다', async () => {
    entries.length = 0
    await call(handler, { method: 'GET', url: '/page-2?secret=1', host: HOST.fixture })
    assert.equal(entries[0].method, 'GET')
    assert.equal(entries[0].path, '/page-2')
  })

  it('dist 밖 파일을 주지 않는다', async () => {
    for (const url of ['/../secret.txt', '/%2e%2e/secret.txt', '/..%2fsecret.txt', '/link.txt', '/nothing.js', '/a%00b']) {
      const res = await call(handler, { method: 'GET', url, host: HOST.fixture })
      assert.equal(res.status, 404, url)
    }
    assert.equal(await resolveStaticFile(dist, '/%E0%A4%A'), null)
  })

  it('허용하지 않은 Host를 거부한다', async () => {
    const res = await call(handler, { method: 'GET', url: '/', host: 'evil.test:5173' })
    assert.equal(res.status, 421)
  })

  it('같은 origin 합성 POST를 기록하고 본문 값은 남기지 않는다', async () => {
    entries.length = 0
    const res = await call(handler, { url: protocol.PATHS.same, host: HOST.fixture, body: synthetic('A') })
    assert.equal(res.status, 200)
    assert.match(res.body, /TEMP-WEBVIEW-FIXTURE/)
    assert.equal(entries[0].outcome, 'received')
    assert.equal(entries[0].run, RUN)
    assert.equal(entries[0].case, 'A')
    assert.equal(JSON.stringify(entries[0]).includes('synthetic'), false)
  })

  it('제출 시도 기록을 도착 기록과 구분한다', async () => {
    entries.length = 0
    const res = await call(handler, { url: protocol.PATHS.attempt, host: HOST.fixture, body: synthetic('B') })
    assert.equal(res.status, 204)
    assert.equal(entries[0].outcome, 'attempt')
  })

  it('CSP 위반을 실행 ID와 함께 기록한다', async () => {
    entries.length = 0
    const body = form({ run: RUN, case: 'B', directive: 'form-action', blockedOrigin: 'http://localhost:18080' })
    const res = await call(handler, { url: protocol.PATHS.violation, host: HOST.fixture, body })
    assert.equal(res.status, 204)
    assert.deepEqual(
      [entries[0].outcome, entries[0].run, entries[0].case, entries[0].directive, entries[0].blockedOrigin],
      ['csp-violation', RUN, 'B', 'form-action', 'http://localhost:18080'],
    )
  })

  it('형식이 어긋난 CSP 위반 기록을 거부한다', async () => {
    const body = form({ run: RUN, case: 'B', directive: 'form-action', blockedOrigin: 'http://a.test/path?x=1' })
    const res = await call(handler, { url: protocol.PATHS.violation, host: HOST.fixture, body })
    assert.equal(res.status, 400)
  })

  it('합성 값이 아니거나 필드가 다르거나 경로와 사례가 맞지 않으면 거부한다', async () => {
    const cases = [
      form({ run: RUN, case: 'A', sample: 'real-value' }),
      synthetic('A', { extra: '1' }),
      form({ run: RUN, case: 'A' }),
      form({ run: 'bad id!', case: 'A', sample: 'synthetic' }),
      synthetic('B'),
    ]
    for (const body of cases) {
      entries.length = 0
      const res = await call(handler, { url: protocol.PATHS.same, host: HOST.fixture, body })
      assert.equal(res.status, 400, body)
      assert.equal(entries[0].run, undefined)
      assert.equal(JSON.stringify(entries[0]).includes('real-value'), false)
    }
  })

  it('폼 형식이 아니면 거부한다', async () => {
    const res = await call(handler, { url: protocol.PATHS.same, host: HOST.fixture, type: 'application/json', body: synthetic('A') })
    assert.equal(res.status, 400)
  })

  it('1KB 제한은 경계값까지 읽고 넘으면 거부한다', async () => {
    const padding = (length) => `run=${'a'.repeat(length)}`
    const atLimit = await call(handler, { url: protocol.PATHS.same, host: HOST.fixture, body: padding(MAX_BODY_BYTES - 4) })
    assert.equal(atLimit.status, 400)
    const over = await call(handler, { url: protocol.PATHS.same, host: HOST.fixture, body: padding(MAX_BODY_BYTES - 3) })
    assert.equal(over.status, 413)
  })

  it('선언 길이가 작아도 실제 본문이 크면 거부한다', async () => {
    const req = fakeRequest({ url: protocol.PATHS.same, host: HOST.fixture, body: 'a'.repeat(MAX_BODY_BYTES + 1) })
    req.headers['content-length'] = '1'
    const res = fakeResponse()
    await handler(req, res)
    assert.equal(res.status, 413)
  })

  it('허용하지 않은 메서드와 경로를 거부한다', async () => {
    assert.equal((await call(handler, { method: 'PUT', url: '/', host: HOST.fixture })).status, 405)
    assert.equal((await call(handler, { url: '/other', host: HOST.fixture, body: synthetic('A') })).status, 404)
  })
})

describe('수신기 처리기', () => {
  it('허용 사례를 도착으로 기록하고 CSP를 붙이지 않는다', async () => {
    for (const caseId of ['C0', 'C1', 'C2', 'B', 'D']) {
      const entries = []
      const handler = createReceiverHandler({ log: (entry) => entries.push(entry), now: () => 0 })
      const res = await call(handler, { url: protocol.PATHS.receive, host: HOST.receiver, body: synthetic(caseId) })
      assert.equal(res.status, 200)
      assert.equal(hasCsp(res), false)
      assert.deepEqual([entries[0].server, entries[0].outcome, entries[0].case], ['receiver', 'received', caseId])
    }
  })

  it('허용하지 않은 사례·메서드·Host·경로·크기를 거부한다', async () => {
    const handler = createReceiverHandler({ log: () => {}, now: () => 0 })
    const post = { url: protocol.PATHS.receive, host: HOST.receiver }
    assert.equal((await call(handler, { ...post, body: synthetic('A') })).status, 400)
    assert.equal((await call(handler, { ...post, method: 'GET' })).status, 405)
    assert.equal((await call(handler, { ...post, host: 'evil.test:18080', body: synthetic('B') })).status, 421)
    assert.equal((await call(handler, { ...post, url: '/x', body: synthetic('B') })).status, 404)
    assert.equal((await call(handler, { ...post, body: 'a'.repeat(MAX_BODY_BYTES + 1) })).status, 413)
  })
})

describe('시작과 종료', () => {
  function fakeDeps() {
    const handlers = {}
    const signals = {}
    const state = { closed: 0, closedAll: 0, timerMs: null, cleared: 0, exitCode: null, exitCodes: [], listen: null, printed: [] }
    const server = {
      on: (event, listener) => {
        handlers[event] = listener
      },
      listen: (port, host, ready) => {
        state.listen = { port, host }
        state.ready = ready
      },
      close: (done) => {
        state.closed += 1
        done()
      },
      closeAllConnections: () => {
        state.closedAll += 1
      },
    }
    const deps = {
      createServer: () => server,
      setTimer: (fn, ms) => {
        state.timerMs = ms
        state.fire = fn
        return 1
      },
      clearTimer: () => {
        state.cleared += 1
      },
      onSignal: (signal, listener) => {
        signals[signal] = listener
      },
      print: (message) => state.printed.push(message),
      printError: (message) => state.printed.push(message),
      setExitCode: (code) => {
        state.exitCode = code
        state.exitCodes.push(code)
      },
    }
    const logSink = {
      closed: 0,
      listener: null,
      failure: null,
      close(done) {
        this.closed += 1
        done()
      },
      onError(listener) {
        if (this.failure) listener(this.failure)
        else this.listener = listener
      },
      fail(error) {
        this.failure = error
        this.listener?.(error)
      },
    }
    return { deps, state, handlers, signals, logSink, server }
  }

  it('loopback에만 바인딩하고 시작 뒤 30분 타이머를 건다', () => {
    const { deps, state, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 5173, handler: () => {}, logSink, deps })
    assert.deepEqual(state.listen, { port: 5173, host: '127.0.0.1' })
    assert.equal(state.timerMs, null)
    state.ready()
    assert.equal(state.timerMs, AUTO_STOP_MS)
    assert.equal(AUTO_STOP_MS, 1_800_000)
  })

  it('자동 종료와 신호 종료는 서버와 기록을 한 번만 닫는다', () => {
    const { deps, state, signals, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 18080, handler: () => {}, logSink, deps })
    state.ready()
    state.fire()
    signals.SIGINT()
    signals.SIGTERM()
    assert.equal(state.closed, 1)
    assert.equal(state.closedAll, 1)
    assert.equal(logSink.closed, 1)
  })

  it('SIGINT로 멈추면 타이머를 해제한다', () => {
    const { deps, state, signals, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 18080, handler: () => {}, logSink, deps })
    state.ready()
    signals.SIGINT()
    assert.equal(state.cleared, 1)
  })

  it('포트 충돌이면 실패 코드를 남기고 기록을 닫는다', () => {
    const { deps, state, handlers, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 5173, handler: () => {}, logSink, deps })
    handlers.error({ code: 'EADDRINUSE' })
    assert.equal(state.exitCode, 1)
    assert.equal(logSink.closed, 1)
    assert.match(state.printed.join(' '), /EADDRINUSE/)
  })

  it('실행 중 기록 실패면 서버·연결·기록을 닫고 타이머를 해제하며 실패 코드를 남긴다', () => {
    const { deps, state, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 18080, handler: () => {}, logSink, deps })
    state.ready()
    logSink.fail({ code: 'ENOSPC', message: 'run=secret-body' })
    assert.equal(state.exitCode, 1)
    assert.equal(state.cleared, 1)
    assert.equal(state.closed, 1)
    assert.equal(state.closedAll, 1)
    assert.equal(logSink.closed, 1)
    const printed = state.printed.join(' ')
    assert.match(printed, /기록 실패: ENOSPC/)
    assert.doesNotMatch(printed, /secret-body/)
  })

  it('구독 전에 기록이 실패했으면 포트를 열지 않고 타이머·시작 안내도 만들지 않는다', () => {
    const { deps, state, logSink } = fakeDeps()
    logSink.failure = { code: 'EACCES' }
    startLoopbackServer({ name: 't', port: 5173, handler: () => {}, logSink, deps })
    assert.equal(state.listen, null)
    assert.equal(state.timerMs, null)
    assert.equal(state.exitCode, 1)
    assert.equal(logSink.closed, 1)
    assert.doesNotMatch(state.printed.join(' '), /시작:/)
  })

  it('시작 완료 전 실패 뒤 늦은 시작 콜백은 포트를 다시 닫고 타이머·시작 안내를 만들지 않는다', () => {
    const { deps, state, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 5173, handler: () => {}, logSink, deps })
    logSink.fail({ code: 'EACCES' })
    state.ready()
    assert.equal(state.timerMs, null)
    assert.equal(state.closed, 2)
    assert.equal(logSink.closed, 1)
    assert.equal(state.exitCode, 1)
    assert.doesNotMatch(state.printed.join(' '), /시작:/)
  })

  it('반복 오류·신호·자동 종료가 겹쳐도 종료와 보고는 한 번이고 실패 코드를 0으로 덮지 않는다', () => {
    const { deps, state, signals, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 18080, handler: () => {}, logSink, deps })
    state.ready()
    logSink.fail({ code: 'EIO' })
    logSink.fail({ code: 'EIO' })
    signals.SIGTERM()
    signals.SIGINT()
    state.fire()
    assert.equal(state.closed, 1)
    assert.equal(state.closedAll, 1)
    assert.equal(logSink.closed, 1)
    assert.equal(state.printed.filter((line) => line.includes('기록 실패')).length, 1)
    assert.deepEqual(state.exitCodes, [1])
  })

  it('정상 종료 중 기록이 실패해도 중복 종료 없이 실패 코드를 남긴다', () => {
    const { deps, state, signals, logSink } = fakeDeps()
    startLoopbackServer({ name: 't', port: 18080, handler: () => {}, logSink, deps })
    state.ready()
    signals.SIGTERM()
    assert.deepEqual(state.exitCodes, [])
    logSink.fail({ code: 'EIO' })
    assert.equal(state.closed, 1)
    assert.equal(logSink.closed, 1)
    assert.deepEqual(state.exitCodes, [1])
  })

  it('기록 경로는 fixture 폴더 밖의 절대 경로만 받는다', () => {
    assert.equal(parseLogPath([], ROOT), null)
    assert.equal(parseLogPath(['--log', 'relative.jsonl'], ROOT), null)
    assert.equal(parseLogPath(['--log', path.join(ROOT, 'logs/a.jsonl')], ROOT), null)
    assert.equal(parseLogPath(['--log', path.join(ROOT, '../escape/../webview-fixture/a')], ROOT), null)
    assert.equal(parseLogPath(['--log', '/tmp/a.jsonl'], ROOT), '/tmp/a.jsonl')
  })

  it('기록기는 항목을 한 줄씩 쓰고 호출 전에는 열지 않는다', () => {
    const lines = []
    let opened = 0
    const sink = openJsonlLog('/tmp/x', () => {
      opened += 1
      return { on: () => {}, write: (line) => lines.push(line), end: (done) => done() }
    })
    sink.write({ a: 1 })
    assert.equal(opened, 1)
    assert.deepEqual(lines, ['{"a":1}\n'])
  })

  /** 가짜 쓰기 스트림. 오류 이벤트는 리스너가 없으면 EventEmitter 규칙대로 예외가 된다. */
  function fakeStream({ endCallsBack = true } = {}) {
    const stream = new EventEmitter()
    Object.assign(stream, { lines: [], ended: 0, destroyed: 0 })
    stream.write = (line) => stream.lines.push(line)
    stream.end = (done) => {
      stream.ended += 1
      if (endCallsBack) done()
    }
    stream.destroy = () => {
      stream.destroyed += 1
    }
    return stream
  }

  it('열기 오류를 예외 없이 붙잡아 나중에 구독해도 전달하고 이후 쓰기를 버린다', () => {
    const stream = fakeStream()
    const sink = openJsonlLog('/tmp/x', () => stream)
    stream.emit('error', { code: 'EACCES' })
    const seen = []
    sink.onError((error) => seen.push(error.code))
    sink.write({ a: 1 })
    let done = 0
    sink.close(() => {
      done += 1
    })
    assert.deepEqual(seen, ['EACCES'])
    assert.deepEqual(stream.lines, [])
    assert.equal(stream.ended, 0)
    assert.equal(stream.destroyed, 1)
    assert.equal(done, 1)
  })

  it('실제 파일 스트림의 열기 실패도 미처리 예외 없이 구독자에게 전달한다', async () => {
    const dir = await mkdtemp(path.join(os.tmpdir(), 'fixture-log-'))
    try {
      const sink = openJsonlLog(path.join(dir, 'missing', 'x.jsonl'), realCreateWriteStream)
      const error = await new Promise((resolve) => sink.onError(resolve))
      assert.equal(error.code, 'ENOENT')
      await new Promise((resolve) => sink.close(resolve))
    } finally {
      await rm(dir, { recursive: true, force: true })
    }
  })

  it('반복 쓰기 오류는 구독자에게 한 번만 알린다', () => {
    const stream = fakeStream()
    const sink = openJsonlLog('/tmp/x', () => stream)
    const seen = []
    sink.onError((error) => seen.push(error.code))
    sink.write({ a: 1 })
    stream.emit('error', { code: 'ENOSPC' })
    stream.emit('error', { code: 'ENOSPC' })
    sink.write({ a: 2 })
    assert.deepEqual(seen, ['ENOSPC'])
    assert.equal(stream.lines.length, 1)
  })

  it('닫는 중 오류가 나면 완료 콜백을 한 번만 부르고 오류를 알린다', () => {
    const stream = fakeStream({ endCallsBack: false })
    const sink = openJsonlLog('/tmp/x', () => stream)
    const seen = []
    sink.onError((error) => seen.push(error.code))
    let done = 0
    sink.close(() => {
      done += 1
    })
    sink.write({ late: true })
    stream.emit('error', { code: 'EIO' })
    stream.emit('error', { code: 'EIO' })
    sink.close(() => {
      done += 1
    })
    assert.equal(done, 1)
    assert.deepEqual(seen, ['EIO'])
    assert.deepEqual(stream.lines, [])
  })
})

describe('규약과 소스 규칙', () => {
  it('포트와 주소를 고정한다', () => {
    assert.equal(protocol.FIXTURE_PORT, 5173)
    assert.equal(protocol.RECEIVER_PORT, 18080)
    assert.equal(protocol.LOOPBACK_HOST, '127.0.0.1')
    assert.equal(protocol.RECEIVER_URL, 'http://localhost:18080/w6/receive')
  })

  it('차단 주소에서 경로와 쿼리를 버린다', () => {
    assert.equal(protocol.summarizeBlockedUri('http://localhost:18080/w6?x=1'), 'http://localhost:18080')
    assert.equal(protocol.summarizeBlockedUri('inline'), 'inline')
    assert.equal(protocol.summarizeBlockedUri('not a url'), 'other')
  })

  it('소스 파일마다 표식이 있고 index.html에 CSP가 없다', async () => {
    const files = [
      '.gitignore', '.npmrc', 'README.md', 'index.html', 'package.json', 'vite.config.js',
      'shared/protocol.js', 'src/App.jsx', 'src/main.jsx', 'src/runState.js', 'src/violations.js',
      'server/fixture-handler.mjs', 'server/http-common.mjs', 'server/lifecycle.mjs',
      'server/receiver-handler.mjs', 'server/run-fixture.mjs', 'server/run-receiver.mjs',
      'scripts/check-dist.mjs', 'test/fixture.test.mjs',
    ]
    for (const file of files) {
      const text = await readFile(path.join(ROOT, file), 'utf8')
      assert.match(text, /TEMP-WEBVIEW-FIXTURE/, file)
    }
    const html = await readFile(path.join(ROOT, 'index.html'), 'utf8')
    assert.doesNotMatch(html, /<meta[^>]+content-security-policy/i)
  })

  it('frontend와 CDN을 참조하지 않는다', async () => {
    const files = ['index.html', 'package.json', 'vite.config.js', 'src/App.jsx', 'src/main.jsx']
    for (const file of files) {
      const text = await readFile(path.join(ROOT, file), 'utf8')
      assert.doesNotMatch(text, /\.\.\/\.\.\/\.\.\/frontend|unpkg\.com|cdn\.|jsdelivr/, file)
    }
  })
})
