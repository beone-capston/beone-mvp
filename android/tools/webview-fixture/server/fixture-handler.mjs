// TEMP-WEBVIEW-FIXTURE 화면 제공과 같은 origin POST 기록. 정적 파일은 dist 안으로 제한한다.
import { readFile, realpath } from 'node:fs/promises'
import path from 'node:path'
import {
  CASES_BY_PATH,
  FIXTURE_PORT,
  MARKER,
  PATHS,
  VIOLATION_CASES,
  isValidBlockedOrigin,
  isValidDirective,
  isValidRunId,
} from '../shared/protocol.js'
import {
  BodyTooLargeError,
  isAllowedHost,
  makeLogEntry,
  parseExactForm,
  parseSyntheticPost,
  pathnameOf,
  readLimitedBody,
  respond,
} from './http-common.mjs'

const CLIENT_ROUTES = new Set(['/', '/page-2'])
const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
}
const HTML = { 'Content-Type': 'text/html; charset=utf-8' }
const TEXT = { 'Content-Type': 'text/plain; charset=utf-8' }

/** 요청 경로를 dist 안의 실제 파일로 바꾼다. 밖을 가리키면 null. */
export async function resolveStaticFile(distDir, pathname) {
  let decoded
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }
  if (decoded.includes('\0')) return null
  const root = await realpath(distDir)
  const relative = CLIENT_ROUTES.has(decoded) ? 'index.html' : `.${decoded}`
  try {
    const real = await realpath(path.resolve(root, relative))
    return real.startsWith(root + path.sep) ? real : null
  } catch {
    return null
  }
}

function sameOriginPage(run) {
  return `<!doctype html><html lang="ko"><head><meta charset="UTF-8"><title>${MARKER}</title></head>`
    + `<body><h1>${MARKER}</h1><p>같은 origin POST를 받았습니다. 실행 ID: ${run}</p><p><a href="/">첫 페이지로</a></p></body></html>`
}

/** dist 안의 정적 파일을 응답하고 결과를 기록한다. dist 밖이거나 없는 파일이면 404로 응답한다. */
async function serveStatic(req, res, distDir, pathname, log) {
  const file = await resolveStaticFile(distDir, pathname)
  if (!file) {
    respond(req, res, 404, TEXT, 'not found')
    log({ status: 404, outcome: 'not-found' })
    return
  }
  const type = MIME_TYPES[path.extname(file)] ?? 'application/octet-stream'
  respond(req, res, 200, { 'Content-Type': type }, await readFile(file))
  log({ status: 200, outcome: 'served' })
}

/**
 * 같은 origin POST 경로(same·attempt·csp-violation)를 처리한다.
 * 본문을 1KB까지 읽어 경로별 허용 필드·사례만 받고, 기록에는 본문 값 대신 run·case 같은 요약만 남긴다.
 * 형식이 맞지 않으면 400, 다른 경로면 404로 응답한다.
 */
async function handlePost(req, res, pathname, log) {
  if (pathname !== PATHS.same && pathname !== PATHS.attempt && pathname !== PATHS.violation) {
    respond(req, res, 404, TEXT, 'not found')
    log({ status: 404, outcome: 'not-found' })
    return
  }
  const body = await readLimitedBody(req)
  if (pathname === PATHS.violation) {
    const form = parseExactForm(req, body, ['run', 'case', 'directive', 'blockedOrigin'])
    const valid = form
      && isValidRunId(form.run)
      && VIOLATION_CASES.includes(form.case)
      && isValidDirective(form.directive)
      && isValidBlockedOrigin(form.blockedOrigin)
    if (!valid) {
      respond(req, res, 400, TEXT, 'bad request')
      log({ status: 400, outcome: 'rejected', bodyBytes: body.length })
      return
    }
    respond(req, res, 204, {})
    log({
      status: 204,
      outcome: 'csp-violation',
      run: form.run,
      case: form.case,
      directive: form.directive,
      blockedOrigin: form.blockedOrigin,
    })
    return
  }
  const form = parseSyntheticPost(req, body, CASES_BY_PATH[pathname])
  if (!form) {
    respond(req, res, 400, TEXT, 'bad request')
    log({ status: 400, outcome: 'rejected', bodyBytes: body.length })
    return
  }
  if (pathname === PATHS.attempt) {
    respond(req, res, 204, {})
    log({ status: 204, outcome: 'attempt', run: form.run, case: form.case, bodyBytes: body.length })
    return
  }
  respond(req, res, 200, HTML, sameOriginPage(form.run))
  log({ status: 200, outcome: 'received', run: form.run, case: form.case, bodyBytes: body.length })
}

/** 화면 서버의 요청 처리기를 만든다. 소켓을 열지 않는다. */
export function createFixtureHandler({ distDir, log, now = Date.now }) {
  return async (req, res) => {
    const write = (fields) => log(makeLogEntry('fixture', req, now, fields))
    try {
      if (!isAllowedHost(req.headers.host, FIXTURE_PORT)) {
        respond(req, res, 421, TEXT, 'misdirected')
        write({ status: 421, outcome: 'bad-host' })
        return
      }
      const pathname = pathnameOf(req.url)
      if (pathname === null) {
        respond(req, res, 400, TEXT, 'bad request')
        write({ status: 400, outcome: 'rejected' })
        return
      }
      if (req.method === 'GET' || req.method === 'HEAD') {
        await serveStatic(req, res, distDir, pathname, write)
        return
      }
      if (req.method === 'POST') {
        await handlePost(req, res, pathname, write)
        return
      }
      respond(req, res, 405, { ...TEXT, Allow: 'GET, HEAD, POST' }, 'method not allowed')
      write({ status: 405, outcome: 'rejected' })
    } catch (error) {
      if (error instanceof BodyTooLargeError) {
        respond(req, res, 413, TEXT, 'too large')
        write({ status: 413, outcome: 'too-large' })
        return
      }
      respond(req, res, 500, TEXT, 'error')
      write({ status: 500, outcome: 'error' })
    }
  }
}
