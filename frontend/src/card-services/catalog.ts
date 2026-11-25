import rules from './rules.json'

type Fact<T> = { value?: T; unresolved?: string }
type CapTier = { minimumPerformance: number; monthlyCap: number }

export type Bucket = { id: string; tiers: CapTier[]; parent: string | null }
export type Service = {
  id: string
  kind: string
  target: { type: string; classes?: string[] }
  tiers: { minimumPerformance: number; calculation: Fact<{ type: string; basisPoints?: number; won?: number }> }[]
  limitBuckets: string[]
  excludedClasses: string[]
  unevaluatedExclusions: string[]
  minimumAmount: number
  maximumTargetAmount: number | null
  requiresSelection: boolean
}

export type Product = {
  cardId: string
  ruleVersion: string
  status: string
  validity: { from: string; to: string | null }
  sources: { uri: string; locator: string }[]
  policy: {
    performanceExcludedClasses: string[]
    excludesBenefitedSales: Fact<boolean>
    cancellationAttribution: Fact<string>
    newCardGrace: { value: { span: string; days: number | null; deemedPerformance: number; services: string[] } }
  }
  limitBuckets: Bucket[]
  services: Service[]
}

export type ProductId = keyof typeof rules
export const catalog: Record<ProductId, Product> = rules

type Detail = {
  name: string
  target?: string
  payment?: string
  note?: string
  payout?: string
  frequency?: string
  maximumBenefit?: number
  targetLimit?: string
  noAmountLimits?: boolean
}

export const details: Record<ProductId, Record<string, Detail>> = {
  // The additional descriptions below are scoped to the versions in rules.json.
  // Evidence and product terms are recorded in docs/rules/{cardProductId}.md.
  '09174': {
    GREAT: { name: '스타벅스' },
    ENJOY: { name: '버거·패스트푸드', target: '맥도날드·버거킹·롯데리아·쉐이크쉑', note: '명시된 브랜드 외 가맹점은 확인 필요' },
    CHECK: { name: '간편결제', target: '삼성페이·네이버페이·카카오페이·KB국민 앱카드', payment: '대상 간편결제', note: '대상 업종에서 간편결제와 업종 할인이 중복 적용되면 각각의 월 한도에서 차감됩니다.' },
    BASIC_TELECOM: { name: '이동통신요금', payment: '이동통신요금 자동납부' },
    BASIC_TRANSIT: { name: '대중교통·택시', target: '버스·지하철·택시', note: '버스·지하철은 명세서에 기재된 이용일 기준' },
  },
  '01664': {
    CGV: { name: 'CGV', target: 'CGV 영화관, 직영 홈페이지·앱 예매', maximumBenefit: 7000 },
    OUTBACK_VIPS: { name: '아웃백·VIPS', maximumBenefit: 10000 },
    STARBUCKS: { name: '스타벅스', maximumBenefit: 4000 },
    THEME_PARK: { name: '에버랜드·롯데월드', target: '에버랜드·롯데월드 자유이용권', maximumBenefit: 25000 },
    GS25: { name: 'GS25', maximumBenefit: 1000 },
    KYOBO: { name: '교보문고', target: '매장 및 직영 홈페이지, 앱은 상품 페이지 기준', maximumBenefit: 2500 },
    TRANSIT: { name: '대중교통', target: '시내버스·지하철 및 고속·시외버스', payment: '시내버스·지하철은 후불교통', note: '시내버스·지하철은 이용내역서 기재일 기준', targetLimit: '월 이용금액 20,000원' },
    TELECOM: { name: '이동통신요금', payment: '이동통신요금 자동이체', frequency: '월 1회' },
  },
  '01914': {
    CU: { name: 'CU' },
    STARBUCKS: { name: '스타벅스', target: '스타벅스 매장·사이렌오더' },
    CGV: { name: 'CGV', target: 'CGV 영화관, 인터넷 예매는 직영 홈페이지·앱' },
    TENBYTEN: { name: '텐바이텐', target: '텐바이텐 직영 온라인몰·매장' },
    SIMPLE_PAY: { name: '온라인 간편결제', payment: '네이버페이·카카오페이·SSG페이의 온라인 가맹점 결제' },
    BEAUTY: { name: '올리브영·안경/렌즈점', target: '올리브영·안경점·렌즈점' },
    BOOKSTORE: { name: '온라인 서점', target: '예스24·교보문고 직영 홈페이지·앱' },
    CULTURE: { name: '공연·티켓', target: '인터파크 티켓의 공연 예매' },
    TRANSIT: { name: '대중교통', target: '버스·지하철·시외버스' },
  },
  '09271': {
    BASE: { name: '국내외 전 가맹점 기본 적립', target: '국내외 전 가맹점', payout: '다음 영업일 포인트리 적립. 1포인트리 = 1원, 유효기간 60개월. 취소 시 차감.', note: '선택 할인과 기본 적립의 중복 적용은 확인 필요' },
    FUEL: { name: '주유 선택 할인', target: '전 주유소·충전소', noAmountLimits: true, note: 'GS칼텍스 공시 보통휘발유가 기준. 경유·등유·LPG는 보통휘발유가로 환산합니다.' },
    STORE: { name: '생활업종 선택 할인', target: '이마트·롯데마트·홈플러스, GS25·CU, 커피·음료전문점 및 약국(KB 업종 분류)', noAmountLimits: true, note: '마트·편의점·커피·약국이 하나의 생활업종 한도를 공유합니다.' },
  },
}

export const otherServices: Partial<Record<ProductId, { name: string; target: string; performance: string; note?: string }>> = {
  '01664': {
    name: '금융수수료 면제',
    target: '타행이체 및 KB국민은행 자동화기기',
    performance: '최근 1개월 동일 계열 카드 합산 매입실적 70만원 이상',
  },
  '09271': {
    name: '티타늄 서비스',
    target: '공항·호텔 발레파킹, 공항라운지',
    performance: '전월 이용실적 30만원 이상, 실적 유예 없음',
    note: 'Master 브랜드는 공항라운지·F&B·국내 면세점 할인 등 추가 혜택을 제공합니다. 제공 조건은 브랜드별 안내를 확인해 주세요.',
  },
}

export function bucketName(id: string) {
  if (id === 'BASIC') return '통신·교통 통합 한도'
  if (id === 'COMMON') return '월 통합 할인 한도'
  return '월 할인 한도'
}
