import { createHash, timingSafeEqual } from 'node:crypto'
import type { Plugin } from 'vite'

const loopbackHost = /^(?:localhost|127\.0\.0\.1|\[::1\])(?::\d{1,5})?$/
const peers = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1'])

export function localPreview(verifier: string): Plugin {
  return {
    name: 'beone-local-preview',
    apply: 'serve',
    configureServer(server) {
      if (!/^[0-9a-f]{64}$/.test(verifier)) return
      const expected = Buffer.from(verifier, 'hex')
      server.middlewares.use('/__beone_local_preview/login', async (request, response) => {
        response.setHeader('Cache-Control', 'no-store')
        const host = request.headers.host
        const sameOrigin = host && request.headers.origin === 'http://' + host &&
          loopbackHost.test(host)
        if (request.method !== 'POST' || !sameOrigin || !peers.has(request.socket.remoteAddress ?? '')) {
          response.writeHead(404).end()
          return
        }
        try {
          let body = ''
          for await (const chunk of request) {
            body += chunk
            if (body.length > 2048) {
              response.writeHead(413).end()
              return
            }
          }
          const { email, password } = JSON.parse(body)
          if (typeof email !== 'string' || typeof password !== 'string') {
            response.writeHead(400).end()
            return
          }
          const hash = createHash('sha256').update(email.trim().toLowerCase() + '\n' + password).digest()
          response.writeHead(timingSafeEqual(hash, expected) ? 204 : 401).end()
        } catch {
          response.writeHead(400).end()
        }
      })
    },
  }
}
