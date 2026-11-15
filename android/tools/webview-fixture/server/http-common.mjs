// TEMP-WEBVIEW-FIXTURE 두 서버가 함께 쓰는 요청 처리 도구. import만으로 아무것도 시작하지 않는다.
import { SAMPLE, isValidRunId } from '../shared/protocol.js'

export const MAX_BODY_BYTES = 1024
const FORM_TYPE = 'application/x-www-form-urlencoded'

// CSP는 일부러 넣지 않는다. 넣으면 네이티브 판정과 구분되지 않는다.
export const BASE_HEADERS = Object.freeze({
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff',
})

/** 본문 크기 제한을 넘었을 때 쓰는 오류. */
export class BodyTooLargeError extends Error {}

/** 본문을 제한 크기까지만 읽는다. */
export async function readLimitedBody(req, limit = MAX_BODY_BYTES) {
  const declared = Number(req.headers['content-length'] ?? 0)
  if (declared > limit) throw new BodyTooLargeError()
  const chunks = []
  let total = 0
  for await (const chunk of req) {
    total += chunk.length
    if (total > limit) throw new BodyTooLargeError()
    chunks.push(chunk)
  }
  return Buffer.concat(chunks)
}

/** 요청의 Host가 loopback의 해당 포트인지 확인한다. */
export function isAllowedHost(host, port) {
  return host === `localhost:${port}` || host === `127.0.0.1:${port}`
}

/** 요청 주소에서 쿼리를 뺀 경로만 얻는다. 해석할 수 없으면 null. */
export function pathnameOf(url) {
  try {
    return new URL(url, 'http://localhost').pathname
  } catch {
    return null
  }
}

/** 폼 본문이 정확히 요구한 필드만 가지는지 확인해 값을 돌려준다. 맞지 않으면 null. */
export function parseExactForm(req, body, fields) {
  const contentType = String(req.headers['content-type'] ?? '').split(';')[0].trim().toLowerCase()
  if (contentType !== FORM_TYPE) return null
  const params = new URLSearchParams(body.toString('utf8'))
  const keys = [...params.keys()]
  if (keys.length !== fields.length || new Set(keys).size !== keys.length) return null
  if (!fields.every((field) => params.has(field))) return null
  return Object.fromEntries(params)
}

/** 합성 POST 본문을 경로별 허용 사례로 검증한다. 맞지 않으면 null. */
export function parseSyntheticPost(req, body, allowedCases) {
  const form = parseExactForm(req, body, ['run', 'case', 'sample'])
  if (!form) return null
  if (!isValidRunId(form.run) || !allowedCases.includes(form.case) || form.sample !== SAMPLE) return null
  return form
}

/** 응답을 보낸다. HEAD에는 본문을 보내지 않는다. */
export function respond(req, res, status, headers, body = '') {
  res.writeHead(status, { ...BASE_HEADERS, ...headers })
  res.end(req.method === 'HEAD' ? undefined : body)
}

/** 최소 기록 항목을 만든다. 본문 값과 헤더 전문은 남기지 않는다. */
export function makeLogEntry(server, req, now, fields) {
  const path = pathnameOf(req.url)
  return {
    time: new Date(now()).toISOString(),
    server,
    method: req.method,
    path: path === null ? 'invalid' : path.slice(0, 128),
    ...fields,
  }
}
