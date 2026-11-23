import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App.tsx'

describe('B:ONE 지갑 메인화면', () => {
  it('빠른결제를 기본 선택하고 예시 카드와 부가서비스를 표시한다', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: '빠른결제' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getAllByRole('article')).toHaveLength(4)
    expect(
      screen.getAllByRole('button', { name: / 부가서비스$/ }),
    ).toHaveLength(4)
    expect(
      screen.getByText('실제 결제 및 금융거래는 발생하지 않습니다.'),
    ).toBeInTheDocument()
  })

  it('상단과 부가서비스 버튼은 준비 중 안내를 제공한다', () => {
    render(<App />)
    for (const name of [
      '카드 추가',
      '마이페이지',
      '올라운드 티타늄카드 부가서비스',
      '노리 체크카드 부가서비스',
    ]) {
      fireEvent.click(screen.getByRole('button', { name }))
      expect(screen.getByRole('status')).toHaveTextContent(
        name + ' 기능은 준비 중이에요.',
      )
      fireEvent.click(screen.getByRole('button', { name: '안내 닫기' }))
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    }
  })

  it('메뉴를 바꾸면 선택 표시와 준비 중 화면이 바뀌고 빠른결제로 돌아온다', () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: '혜택' }))
    expect(screen.getByRole('heading', { name: '혜택' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '혜택' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(
      screen.getByRole('button', { name: '빠른결제' }),
    ).not.toHaveAttribute('aria-current')
    fireEvent.click(screen.getByRole('button', { name: '모의결제' }))
    expect(
      screen.getByRole('heading', { name: '모의결제' }),
    ).toBeInTheDocument()
    expect(screen.getByText('준비 중이에요')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '빠른결제로 돌아가기' }))
    expect(
      screen.getByRole('heading', { name: '나의 모든 카드, 한곳에.' }),
    ).toBeInTheDocument()
  })

  it('카드를 가로로 넘기면 카드 이름과 페이지 표시가 바뀐다', () => {
    render(<App />)
    const track = screen.getByLabelText('예시 카드 목록')
    Object.defineProperty(track.children[0], 'offsetLeft', { value: 32 })
    Object.defineProperty(track.children[1], 'offsetLeft', { value: 360 })
    fireEvent.scroll(track, { target: { scrollLeft: 328 } })
    expect(
      screen.getByRole('button', { name: '청춘대로 톡톡카드 보기' }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(
      screen.getByRole('button', { name: '올라운드 티타늄카드 보기' }),
    ).toHaveAttribute('aria-pressed', 'false')
  })
})
