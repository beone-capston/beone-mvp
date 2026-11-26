import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { WalletRuntime } from './WalletRuntime'
import { AppStartup } from './startup/AppStartup'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppStartup>
      <WalletRuntime />
    </AppStartup>
  </StrictMode>,
)
