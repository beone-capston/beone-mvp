import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AppStartup } from './startup/AppStartup'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppStartup>
      <App />
    </AppStartup>
  </StrictMode>,
)
