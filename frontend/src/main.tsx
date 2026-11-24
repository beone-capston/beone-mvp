import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { memberCardSource } from './cards/memberCardSource'
import { mockCardSource } from './data/portfolio'
import { AppStartup } from './startup/AppStartup'

// 배포 빌드는 서버의 보유 카드를 쓰고, 개발용 목 결제 모드에서는 디자인 샘플을 쓴다
const mockCards = import.meta.env.DEV && import.meta.env.VITE_PAYMENT_API_MODE === 'mock'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppStartup>
      <App cardSource={mockCards ? mockCardSource : memberCardSource} />
    </AppStartup>
  </StrictMode>,
)
