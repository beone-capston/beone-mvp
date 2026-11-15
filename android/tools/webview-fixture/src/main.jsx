// TEMP-WEBVIEW-FIXTURE 임시 화면 시작점.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App.jsx'
import { installViolationRecorder } from './violations.js'

installViolationRecorder()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
