import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AuthEntry } from './auth/AuthEntry'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthEntry>
      <App />
    </AuthEntry>
  </StrictMode>,
)
