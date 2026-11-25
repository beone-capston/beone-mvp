/// <reference types="node" />
import { readdirSync, readFileSync } from 'node:fs'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { mockCardSource } from '../data/portfolio'
import { bucketAmounts, matchesCatalog, monthPeriod, readMonth, requestMessage } from './api'
import type { MonthlyState } from './api'
import { catalog, details, otherServices } from './catalog'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('부가서비스 원본 대조', () => {
  it('현재 규칙과 서비스 및 한도 묶음을 대조한다', () => {
    const expected = { '09174': 5, '01664': 8, '01914': 9, '09271': 3 }
    for (const id of Object.keys(expected) as (keyof typeof expected)[]) {
      const folder = `../backend/src/main/resources/rules/${id}`
      const file = readdirSync(folder).find(name => name.endsWith('.json'))
      const rule = JSON.parse(readFileSync(`${folder}/${file}`, 'utf8'))
      const product = catalog[id]
      for (const key of Object.keys(product) as (keyof typeof product)[]) {
        expect(product[key]).toEqual(rule[key])
      }
      expect(product.services).toHaveLength(expected[id])
      expect(Object.keys(details[id])).toEqual(product.services.map(service => service.id))
    }
    expect(Object.keys(otherServices)).toEqual(['01664', '09271'])
  })

  it('카드별 전체 리스트, 약관, 공유 한도 및 가상 모드 복귀를 제공한다', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
    vi.stubGlobal('scrollTo', vi.fn())
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-25T12:00:00+09:00'))
    try {
      render(<App />)
      fireEvent.click(screen.getByRole('tab', { name: '가상 포트폴리오' }))
      for (const card of mockCardSource.getCards('VIRTUAL_PORTFOLIO')) {
        const id = card.cardProductId as keyof typeof catalog
        fireEvent.click(screen.getByRole('button', { name: card.name + ' 부가서비스' }))
        const list = screen.getByRole('region', { name: '혜택 및 이용 조건' })
        expect(within(list).getAllByRole('article')).toHaveLength(catalog[id].services.length + Number(Boolean(otherServices[id])))
        expect(screen.getByRole('link', { name: 'KB국민카드 상품설명서' })).toHaveAttribute('href', catalog[id].sources[0].uri)
        if (id === '09174') {
          const telecom = within(list).getByRole('article', { name: '이동통신요금' })
          expect(within(telecom).getByText('통신·교통 통합 한도 적용')).toBeInTheDocument()
          expect(within(telecom).queryByText('5,000원')).not.toBeInTheDocument()
        }
        if (id === '01664' || id === '01914') {
          expect(screen.getByRole('heading', { name: '월 통합 할인 한도' })).toBeInTheDocument()
        }
        if (id === '01914') {
          const cu = within(list).getByRole('article', { name: 'CU' })
          expect(within(cu).getByText('건당 1,000원 할인')).toBeInTheDocument()
          expect(within(cu).getByText('월 4,000원')).toBeInTheDocument()
        }
        if (id === '09271') {
          const base = within(list).getByRole('article', { name: '국내외 전 가맹점 기본 적립' })
          expect(within(base).getByText('조건 없음')).toBeInTheDocument()
          expect(within(base).getByText('제한 없음')).toBeInTheDocument()
        }
        fireEvent.click(screen.getByRole('button', { name: '확인' }))
        expect(screen.getByRole('tab', { name: '가상 포트폴리오' })).toHaveAttribute('aria-selected', 'true')
      }
    } finally { vi.useRealTimers() }
  })

  it('서버의 규칙 버전이 바뀌면 설명 데이터 결합을 거부한다', () => {
    const product = catalog['09174']
    const server = {
      cardProductId: product.cardId, cardName: '톡톡', asOf: '2026-11-25',
      ruleStatus: 'VERIFIED', rule: { ruleVersion: product.ruleVersion, verificationStatus: 'VERIFIED' },
      services: product.services.map(service => ({
        serviceId: service.id, name: service.id, benefitKind: service.kind,
        supportStatus: 'SUPPORTED_SINGLE_MONTHLY_LIMIT', limitBucketIds: service.limitBuckets,
        requiresSelection: service.requiresSelection,
      })),
      limitBuckets: product.limitBuckets.map(bucket => ({
        limitBucketId: bucket.id, parentLimitBucketId: bucket.parent, period: 'CALENDAR_MONTH',
        tiers: bucket.tiers.map(tier => ({ minimumPerformanceWon: tier.minimumPerformance, monthlyCapWon: tier.monthlyCap })),
      })),
    }
    expect(matchesCatalog(product, server)).toBe(true)
    expect(matchesCatalog(product, { ...server, rule: { ...server.rule, ruleVersion: 'different-version' } })).toBe(false)
  })
})

describe('월 사용액의 읽기 계약', () => {
  const month = '2026-11'
  const base = { source: 'MANUAL', updatedAt: '2026-11-02T09:00:00+09:00', usable: true }
  const registeredOn = '2026-07-01'
  const state: MonthlyState = {
    month, userCardId: 'uc-1', snapshotAt: base.updatedAt,
    serviceSelectionStatus: 'UNKNOWN', noServiceSelection: null, selectedServices: [],
    limitBucketUsages: [{ ...base, limitBucketId: 'GREAT', value: 0, period: monthPeriod(month) }],
    previousMonthPerformanceWon: { ...base, value: 300000, period: monthPeriod(month, -1) },
    registeredOn: { ...base, value: registeredOn, period: { from: registeredOn, to: registeredOn } },
    afterSnapshot: { approvalCount: 0, cancellationCount: 0 },
  }

  it('정상 0원, 기간 불일치, UNKNOWN, 실적 미충족, 스냅샷 이후 변경을 구분한다', () => {
    const amounts = (input: MonthlyState) => bucketAmounts(catalog['09174'], 'GREAT', month, input)
    expect(amounts(state)).toMatchObject({ used: 0, remaining: 10000, belowTier: false })
    expect(amounts({ ...state, limitBucketUsages: [] }).used).toBeNull()
    expect(amounts({ ...state, limitBucketUsages: [{ ...state.limitBucketUsages[0], period: monthPeriod(month, -1) }] }).used).toBeNull()
    expect(amounts({ ...state, limitBucketUsages: [{ ...state.limitBucketUsages[0], source: 'UNKNOWN' }] }).used).toBeNull()
    expect(amounts({ ...state, previousMonthPerformanceWon: { ...state.previousMonthPerformanceWon, value: -500 } })).toMatchObject({ belowTier: true, remaining: null })
    expect(amounts({ ...state, afterSnapshot: { approvalCount: 1, cancellationCount: 0 } })).toMatchObject({ used: 0, remaining: null })
  })

  it('보유 카드 ID로만 월 상태를 읽고 권한 오류를 그대로 전달한다', async () => {
    const response = (data: unknown) => new Response(JSON.stringify({ data }))
    const fetcher = vi.fn()
      .mockResolvedValueOnce(response({ demoUser: 'VIRTUAL_PORTFOLIO' }))
      .mockResolvedValueOnce(response({ items: [
        { holdingSource: 'MANUAL', mapping: { cardProductId: '09174' }, userCardId: 'wrong-card' },
        { holdingSource: 'SYNTHETIC', cardProductId: '09174', userCardId: 'uc-1' },
      ] }))
      .mockResolvedValueOnce(response(state))
      .mockResolvedValueOnce(new Response('', { status: 401 }))
    vi.stubGlobal('fetch', fetcher)
    const { signal } = new AbortController()
    expect(await readMonth('09174', month, signal)).toEqual(state)
    expect(fetcher.mock.calls[2]).toEqual([
      '/api/v1/me/cards/uc-1/monthly-states/2026-11',
      { signal, headers: { 'X-BeONE-Demo-User': 'VIRTUAL_PORTFOLIO' } },
    ])
    expect(await readMonth('09174', month, signal).catch(requestMessage)).toBe('사용자 인증이 필요해요.')
    expect(fetcher).toHaveBeenCalledTimes(4)
  })
})
