// TEMP-WEBVIEW-FIXTURE 다른 origin 수신기. 합성 POST의 도착만 기록한다.
import { CASES_BY_PATH, MARKER, PATHS, RECEIVER_PORT } from '../shared/protocol.js'
import {
  BodyTooLargeError,
  isAllowedHost,
  makeLogEntry,
  parseSyntheticPost,
  pathnameOf,
  readLimitedBody,
  respond,
} from './http-common.mjs'

const HTML = { 'Content-Type': 'text/html; charset=utf-8' }
const TEXT = { 'Content-Type': 'text/plain; charset=utf-8' }

function arrivalPage(caseId) {
  return `<!doctype html><html lang="ko"><head><meta charset="UTF-8"><title>${MARKER}</title></head>`
    + `<body><h1>${MARKER}</h1><p>수신기에 도착했습니다. 사례: ${caseId}</p></body></html>`
}

/** 수신기의 요청 처리기를 만든다. 소켓을 열지 않는다. */
export function createReceiverHandler({ log, now = Date.now }) {
  return async (req, res) => {
    const write = (fields) => log(makeLogEntry('receiver', req, now, fields))
    try {
      if (!isAllowedHost(req.headers.host, RECEIVER_PORT)) {
        respond(req, res, 421, TEXT, 'misdirected')
        write({ status: 421, outcome: 'bad-host' })
        return
      }
      if (req.method !== 'POST') {
        respond(req, res, 405, { ...TEXT, Allow: 'POST' }, 'method not allowed')
        write({ status: 405, outcome: 'rejected' })
        return
      }
      if (pathnameOf(req.url) !== PATHS.receive) {
        respond(req, res, 404, TEXT, 'not found')
        write({ status: 404, outcome: 'not-found' })
        return
      }
      const body = await readLimitedBody(req)
      const form = parseSyntheticPost(req, body, CASES_BY_PATH[PATHS.receive])
      if (!form) {
        respond(req, res, 400, TEXT, 'bad request')
        write({ status: 400, outcome: 'rejected', bodyBytes: body.length })
        return
      }
      respond(req, res, 200, HTML, arrivalPage(form.case))
      write({ status: 200, outcome: 'received', run: form.run, case: form.case, bodyBytes: body.length })
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
