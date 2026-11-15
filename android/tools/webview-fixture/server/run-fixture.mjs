// TEMP-WEBVIEW-FIXTURE 화면 서버 실행 진입점. 직접 실행할 때만 시작한다.
import { access } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { FIXTURE_PORT } from '../shared/protocol.js'
import { createFixtureHandler } from './fixture-handler.mjs'
import { openJsonlLog, parseLogPath, startLoopbackServer } from './lifecycle.mjs'

const FIXTURE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** 기록 경로와 dist를 확인한 뒤 화면 서버를 시작한다. 조건이 맞지 않으면 종료 코드 2를 남기고 시작하지 않는다. */
async function main() {
  const logPath = parseLogPath(process.argv.slice(2), FIXTURE_ROOT)
  if (!logPath) {
    console.error('사용법: npm run serve:fixture -- --log <fixture 폴더 밖의 절대 경로>')
    process.exitCode = 2
    return
  }
  const distDir = path.join(FIXTURE_ROOT, 'dist')
  try {
    await access(path.join(distDir, 'index.html'))
  } catch {
    console.error('dist/index.html이 없습니다. npm run build를 먼저 실행하세요.')
    process.exitCode = 2
    return
  }
  const logSink = openJsonlLog(logPath)
  const handler = createFixtureHandler({ distDir, log: logSink.write })
  startLoopbackServer({ name: 'fixture', port: FIXTURE_PORT, handler, logSink })
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main()
}
