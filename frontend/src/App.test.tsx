import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App.tsx'

describe('메인화면', () => {
  it('빈 카드 상태와 모의 서비스 안내를 표시한다', () => {
    render(<App />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('더 현명한 한 장.')
    expect(screen.getByText('첫 번째 카드를 기다리고 있어요')).toBeInTheDocument()
    expect(screen.getByText('실제 결제 및 금융거래는 발생하지 않습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '내 카드' })).toHaveAttribute('aria-pressed', 'true')
  })
  it('가상 모드에서 예시 카드 4종과 미확인 상태를 표시하고 복귀한다', () => {
    render(<App />)
    fireEvent.click(screen.getAllByRole('button', { name: '가상 포트폴리오 체험' })[0])
    expect(screen.getByText(/실제 보유 카드가 아닌 시연용/)).toBeInTheDocument()
    expect(screen.getAllByText('월간 상태 확인 필요')).toHaveLength(4)
    expect(screen.getByText('올라운드 티타늄카드')).toBeInTheDocument()
    expect(screen.queryByText(/^0원$/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '내 카드' }))
    expect(screen.queryByText('올라운드 티타늄카드')).not.toBeInTheDocument()
    expect(screen.getByText('첫 번째 카드를 기다리고 있어요')).toBeInTheDocument()
  })
  it('미구현 기능의 안내를 표시하고 닫는다', () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: /^월간 상태 확인/ }))
    expect(screen.getByRole('status')).toHaveTextContent('월간 상태 확인 기능은 준비 중')
    fireEvent.click(screen.getByRole('button', { name: '안내 닫기' }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '카드 등록' }))
    expect(screen.getByRole('status')).toHaveTextContent('카드 관리 기능은 준비 중')
  })
})
