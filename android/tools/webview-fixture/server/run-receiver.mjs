// TEMP-WEBVIEW-FIXTURE 수신기 실행 진입점. 직접 실행할 때만 시작한다.
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { RECEIVER_PORT } from '../shared/protocol.js'
import { openJsonlLog, parseLogPath, startLoopbackServer } from './lifecycle.mjs'
import { createReceiverHandler } from './receiver-handler.mjs'

const FIXTURE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function main() {
  const logPath = parseLogPath(process.argv.slice(2), FIXTURE_ROOT)
  if (!logPath) {
    console.error('사용법: npm run serve:receiver -- --log <fixture 폴더 밖의 절대 경로>')
    process.exitCode = 2
    return
  }
  const logSink = openJsonlLog(logPath)
  const handler = createReceiverHandler({ log: logSink.write })
  startLoopbackServer({ name: 'receiver', port: RECEIVER_PORT, handler, logSink })
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
