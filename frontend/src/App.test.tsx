import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { createMockPaymentGateway } from './payment/mockPayment'
import { mockCardSource } from './data/portfolio'
import type { CardSource } from './data/portfolio'

function preferenceMenu() {
  fireEvent.click(screen.getByRole('button', { name: '결제 선택 방식' }))
}
function chooseRepresentative(name = '올라운드 티타늄카드') {
  preferenceMenu()
  fireEvent.click(screen.getByRole('menuitemradio', { name: '대표 카드 고정' }))
  fireEvent.click(screen.getByRole('radio', { name }))
  fireEvent.click(screen.getByRole('button', { name: '대표 카드로 설정' }))
}
function nativeState(data: Record<string, unknown>) {
  act(() =>
    window.BeoneBridge?.onmessage?.({
      data: JSON.stringify({
        v: 1,
        type: 'state',
        active: false,
        remainingMs: 0,
        reason: 'NOT_AUTHENTICATED',
        authenticating: false,
        ...data,
      }),
    }),
  )
}
function installBridge() {
  const postMessage = vi.fn()
  window.BeoneBridge = { postMessage, onmessage: null }
  return postMessage
}
function sentTypes(postMessage: ReturnType<typeof vi.fn>) {
  return postMessage.mock.calls.map(([json]) => JSON.parse(json).type)
}
function tapCard() {
  fireEvent.click(screen.getByRole('button', { name: '올라운드 티타늄카드 가상카드 인증' }))
}
function layoutCards() {
  const track = screen.getByRole('region', { name: '예시 카드 목록' })
  Array.from(track.children).forEach((node, index) =>
    Object.defineProperty(node, 'offsetLeft', { configurable: true, value: 50 + index * 270 }),
  )
  return track
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn())
  vi.stubGlobal('PointerEvent', MouseEvent)
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: false })),
  )
})
afterEach(() => {
  delete window.BeoneBridge
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('Figma 메인화면 · 포트폴리오 분리', () => {
  it('내 카드·자동 추천으로 시작하며 소개 배너를 제거한다', () => {
    render(<App />)
    expect(screen.getByRole('tab', { name: '내 카드' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
    expect(screen.getAllByRole('article')).toHaveLength(4)
    expect(screen.getByText(/디자인 확인용 샘플 카드입니다/)).toBeInTheDocument()
    expect(screen.queryByText('나의 모든 카드, 한곳에.')).not.toBeInTheDocument()
    expect(screen.getByText('실제 결제 및 금융거래는 발생하지 않습니다.')).toBeInTheDocument()
  })

  it('내 카드와 가상 데이터·상단 액션을 분리하고 하단 메뉴 이동 시 모드를 유지한다', () => {
    const source: CardSource = {
      getCards: (mode) => mockCardSource.getCards(mode).slice(0, mode === 'MY_CARDS' ? 1 : 4),
    }
    render(<App cardSource={source} />)
    expect(screen.getAllByRole('article')).toHaveLength(1)
    fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
    expect(screen.getAllByRole('article')).toHaveLength(4)
    expect(screen.queryByRole('button', { name: '카드 추가' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '마이페이지' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '혜택' }))
    fireEvent.click(screen.getByRole('button', { name: '빠른결제' }))
    expect(screen.getByRole('tab', { name: '가상 포트폴리오' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('등록 카드가 없을 때의 빈 화면과 카드 추가 진입을 제공한다', () => {
    render(
      <App
        cardSource={{
          getCards: (mode) => (mode === 'MY_CARDS' ? [] : mockCardSource.getCards(mode)),
        }}
      />,
    )
    expect(screen.getByText('등록된 카드가 없어요')).toBeInTheDocument()
    fireEvent.click(screen.getAllByRole('button', { name: '카드 추가' })[1])
    expect(screen.getByRole('status')).toHaveTextContent('준비 중')
    preferenceMenu()
    fireEvent.click(screen.getByRole('menuitemradio', { name: '대표 카드 고정' }))
    expect(screen.getByRole('button', { name: '대표 카드로 설정' })).toBeDisabled()
  })

  it('키보드로 모드를 전환하며 열린 메뉴를 닫는다', () => {
    render(<App />)
    preferenceMenu()
    fireEvent.keyDown(screen.getByRole('tab', { name: '내 카드' }), { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: '가상 포트폴리오' })).toHaveFocus()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('새 마운트는 내 카드·자동 추천으로 시작한다', () => {
    const { unmount } = render(<App />)
    chooseRepresentative()
    fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
    unmount()
    render(<App />)
    expect(screen.getByRole('tab', { name: '내 카드' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
  })
})

describe('결제 설정과 대표 카드', () => {
  it('버튼 재클릭·바깥 클릭·Escape로 닫히고 키보드 포커스가 복귀한다', () => {
    render(<App />)
    preferenceMenu()
    expect(screen.getByRole('menuitemradio', { name: '자동 추천' })).toHaveFocus()
    preferenceMenu()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    preferenceMenu()
    fireEvent.pointerDown(document.body)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    preferenceMenu()
    fireEvent.keyDown(screen.getByRole('menuitemradio', { name: '자동 추천' }), {
      key: 'ArrowDown',
    })
    expect(screen.getByRole('menuitemradio', { name: '대표 카드 고정' })).toHaveFocus()
    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveFocus()
  })

  it('유효한 대표 카드 선택 전에는 설정을 완료하지 않는다', () => {
    render(<App />)
    preferenceMenu()
    fireEvent.click(screen.getByRole('menuitemradio', { name: '대표 카드 고정' }))
    expect(screen.getByRole('button', { name: '대표 카드로 설정' })).toBeDisabled()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
    chooseRepresentative()
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent(
      '대표 카드 고정',
    )
    expect(screen.getByText('고정된 대표 카드: 올라운드 티타늄카드')).toBeInTheDocument()
  })

  it('모드별 대표 카드 설정을 보존하고 변경할 수 있다', () => {
    render(<App />)
    chooseRepresentative()
    fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
    expect(screen.getByRole('button', { name: '결제 선택 방식' })).toHaveTextContent('자동 추천')
    chooseRepresentative('노리 체크카드')
    fireEvent.click(screen.getByRole('tab', { name: '내 카드' }))
    expect(screen.getByText('고정된 대표 카드: 올라운드 티타늄카드')).toBeInTheDocument()
    chooseRepresentative('첵첵 체크카드')
    expect(screen.getByText('고정된 대표 카드: 첵첵 체크카드')).toBeInTheDocument()
  })

  it('캐러셀을 넘겨도 저장된 대표 카드를 바꾸지 않는다', () => {
    render(<App />)
    chooseRepresentative()
    const track = layoutCards()
    fireEvent.scroll(track, { target: { scrollLeft: 270 } })
    expect(screen.getByRole('button', { name: '청춘대로 톡톡카드 보기' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByText('고정된 대표 카드: 올라운드 티타늄카드')).toBeInTheDocument()
    fireEvent.scroll(track, { target: { scrollLeft: 100000 } })
    expect(screen.getByRole('button', { name: '첵첵 체크카드 보기' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    fireEvent.scroll(track, { target: { scrollLeft: -1000 } })
    expect(screen.getByRole('button', { name: '올라운드 티타늄카드 보기' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('페이지 버튼은 해당 카드 위치로 이동을 요청한다', () => {
    render(<App />)
    const track = layoutCards()
    const scrollTo = vi.fn()
    Object.defineProperty(track, 'scrollTo', { value: scrollTo })
    fireEvent.click(screen.getByRole('button', { name: '노리 체크카드 보기' }))
    expect(scrollTo).toHaveBeenCalledWith({ left: 540, behavior: 'smooth' })
  })
})

describe('카드 터치와 부가서비스', () => {
  it('부가서비스는 해당 카드 설명만 열고 인증을 요청하지 않는다', () => {
    const bridge = installBridge()
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: '노리 체크카드 부가서비스' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('노리 체크카드 부가서비스')
    expect(screen.getByRole('dialog')).toHaveTextContent('확인된 설명 데이터를 연결하기 전')
    expect(sentTypes(bridge)).not.toContain('auth.request')
    expect(screen.getByRole('button', { name: '빠른결제' })).toHaveAttribute('aria-current', 'page')
  })

  it('드래그 후 클릭은 인증을 요청하지 않고 별도의 탭만 요청한다', () => {
    const bridge = installBridge()
    render(<App />)
    const card = screen.getByRole('button', { name: '올라운드 티타늄카드 가상카드 인증' })
    fireEvent.pointerDown(card, { clientX: 200, clientY: 100 })
    fireEvent.pointerMove(card, { clientX: 60, clientY: 100 })
    fireEvent.click(card, { detail: 1 })
    expect(sentTypes(bridge)).not.toContain('auth.request')
    fireEvent.pointerDown(card, { clientX: 100, clientY: 100 })
    fireEvent.click(card, { detail: 1 })
    expect(sentTypes(bridge).filter((type) => type === 'auth.request')).toHaveLength(1)
  })

  it('브리지 없는 브라우저에서는 앱 안내만 제공한다', () => {
    render(<App />)
    tapCard()
    expect(screen.getByRole('dialog')).toHaveTextContent('Android 앱에서 사용할 수 있어요')
    expect(screen.getByRole('button', { name: '다시 인증' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '가상 주문서 결제' })).toBeDisabled()
  })
})

describe('BeoneBridge v1 기기 인증 경계', () => {
  it.each([
    ['AUTH_CANCELED', '인증을 취소했어요'],
    ['AUTH_FAILED', '인증에 실패했어요'],
    ['EXPIRED', '60초가 만료됐어요'],
    ['LEFT_APP', '앱을 벗어나'],
    ['NO_RESPONSE', '앱을 완전히 종료'],
  ])('%s 상태를 성공과 구분한다', (reason, message) => {
    installBridge()
    render(<App />)
    tapCard()
    nativeState({ reason })
    expect(screen.getByRole('dialog')).toHaveTextContent(message)
    expect(screen.getByRole('button', { name: '가상 주문서 결제' })).toBeDisabled()
  })

  it('연속 탭으로 중복 인증하지 않고 네이티브 authenticating도 따른다', () => {
    const bridge = installBridge()
    render(<App />)
    tapCard()
    fireEvent.click(screen.getByRole('button', { name: '다시 인증' }))
    expect(sentTypes(bridge).filter((type) => type === 'auth.request')).toHaveLength(1)
    nativeState({ reason: 'AUTH_FAILED', authenticating: true })
    expect(screen.getByRole('button', { name: '다시 인증' })).toBeDisabled()
  })

  it('네이티브 활성 응답만으로 승인하지 않고 확인 메시지가 허용한 뒤 승인한다', async () => {
    const bridge = installBridge()
    const gateway = createMockPaymentGateway(0)
    const approve = vi.spyOn(gateway, 'approve')
    render(<App paymentGateway={gateway} />)
    fireEvent.click(screen.getByRole('button', { name: '모의결제' }))
    fireEvent.click(screen.getByRole('button', { name: /스타벅스 송도점/ }))
    fireEvent.click(screen.getByRole('button', { name: '빠른결제로 이동' }))
    tapCard()
    nativeState({ active: true, remainingMs: 60000, reason: 'ACTIVE' })
    expect(screen.getByLabelText('남은 활성 시간')).toHaveTextContent('60초')
    expect(approve).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '가상 주문서 결제' }))
    expect(sentTypes(bridge)).toContain('payment.check')
    expect(approve).not.toHaveBeenCalled()
    act(() => window.BeoneBridge?.onmessage?.({
      data: '{"v":1,"type":"payment.check.result","allowed":true,"reason":"ACTIVE"}',
    }))
    expect(await screen.findByText('모의 승인 완료')).toBeVisible()
    expect(approve).toHaveBeenCalledTimes(1)
  })

  it('남은 시간이 끝나면 비활성화하고 네이티브 상태를 다시 요청한다', () => {
    vi.useFakeTimers()
    const bridge = installBridge()
    render(<App />)
    tapCard()
    nativeState({ active: true, remainingMs: 1000, reason: 'ACTIVE' })
    act(() => vi.advanceTimersByTime(1200))
    expect(screen.getByRole('button', { name: '가상 주문서 결제' })).toBeDisabled()
    expect(sentTypes(bridge).filter((type) => type === 'state.get').length).toBeGreaterThan(1)
    nativeState({ reason: 'EXPIRED' })
    expect(screen.getByRole('dialog')).toHaveTextContent('60초가 만료됐어요')
  })

  it('허용하지 않은 응답·잘못된 상태·요청 전 payment.check 결과를 무시한다', () => {
    installBridge()
    render(<App />)
    tapCard()
    act(() => window.BeoneBridge?.onmessage?.({ data: '{"v":2,"type":"state","active":true}' }))
    nativeState({ active: true, remainingMs: -1, reason: 'ACTIVE' })
    act(() =>
      window.BeoneBridge?.onmessage?.({
        data: '{"v":1,"type":"payment.check.result","allowed":true,"reason":"ACTIVE"}',
      }),
    )
    expect(screen.queryByRole('button', { name: '모의결제 화면 열기' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('남은 활성 시간')).not.toBeInTheDocument()
  })

  it('payment.check 거부 후에는 승인 요청을 보내지 않는다', () => {
    installBridge()
    const gateway = createMockPaymentGateway(0)
    const approve = vi.spyOn(gateway, 'approve')
    render(<App paymentGateway={gateway} />)
    fireEvent.click(screen.getByRole('button', { name: '모의결제' }))
    fireEvent.click(screen.getByRole('button', { name: /스타벅스 송도점/ }))
    fireEvent.click(screen.getByRole('button', { name: '빠른결제로 이동' }))
    tapCard()
    nativeState({ active: true, remainingMs: 60000, reason: 'ACTIVE' })
    fireEvent.click(screen.getByRole('button', { name: '가상 주문서 결제' }))
    act(() =>
      window.BeoneBridge?.onmessage?.({
        data: '{"v":1,"type":"payment.check.result","allowed":false,"reason":"EXPIRED"}',
      }),
    )
    expect(screen.getByRole('dialog')).toHaveTextContent('60초가 만료됐어요')
    expect(approve).not.toHaveBeenCalled()
  })
})
