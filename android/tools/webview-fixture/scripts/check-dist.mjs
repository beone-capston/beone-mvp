// TEMP-WEBVIEW-FIXTURE 번들 정적 검사. 브라우저 동작을 확인하지 않는다.
import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { MARKER } from '../shared/protocol.js'

const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist')
const problems = []

const html = await readFile(path.join(dist, 'index.html'), 'utf8')
if (!html.includes(MARKER)) problems.push('index.html에 표식 없음')
if (/content-security-policy/i.test(html)) problems.push('index.html에 CSP 있음')
if (/<(script|link)\b[^>]*\b(src|href)=["']https?:/i.test(html)) problems.push('index.html이 외부 자원을 불러옴')

const assets = await readdir(path.join(dist, 'assets'))
const scripts = assets.filter((name) => name.endsWith('.js'))
if (scripts.length === 0) problems.push('JS 번들 없음')
const bundles = await Promise.all(scripts.map((name) => readFile(path.join(dist, 'assets', name), 'utf8')))
if (!bundles.some((text) => text.includes(MARKER))) problems.push('JS 번들에 표식 없음')
if (bundles.some((text) => /from\s*["']https?:|import\(\s*["']https?:/.test(text))) problems.push('JS 번들이 외부 모듈을 불러옴')

if (problems.length > 0) {
  console.error(problems.join('\n'))
  process.exitCode = 1
} else {
  console.log(`dist 정적 검사 통과: JS ${scripts.length}개, 파일 ${assets.length}개`)
}
