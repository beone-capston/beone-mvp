export const merchantLabels = {
  KYOBO_BOOKSTORE: '교보문고 매장',
  KYOBO_ONLINE: '교보문고 온라인',
  SIMPLE_PAY_NAVER_ONLINE: '네이버페이 온라인',
  CASH_ADVANCE: '단기카드대출(현금서비스)',
  UNIVERSITY_TUITION: '대학교 등록금',
  HYPERMARKET_LOTTEMART: '롯데마트',
  THEME_PARK_LOTTEWORLD: '롯데월드',
  NON_APPROVAL_SLIP: '무승인 매출전표',
  BURGER_FASTFOOD: '버거·패스트푸드',
  TRANSIT_BUS_SUBWAY: '버스·지하철',
  VIPS: '빕스',
  GIFT_CARD_PREPAID: '상품권·선불카드',
  TAX: '세금',
  FEES_AND_INTEREST: '수수료·이자',
  STARBUCKS_STORE: '스타벅스 매장',
  TRANSIT_INTERCITY_BUS: '시외버스',
  OUTBACK: '아웃백',
  APARTMENT_FEE: '아파트 관리비',
  OPTICAL_STORE: '안경점',
  PHARMACY: '약국',
  THEME_PARK_EVERLAND: '에버랜드',
  LATE_FEE: '연체료',
  ANNUAL_FEE: '연회비',
  YES24_ONLINE: '예스24 온라인',
  OLIVEYOUNG: '올리브영',
  HYPERMARKET_EMART: '이마트',
  INTERPARK_TICKET_SHOW: '인터파크 공연 예매',
  ORDINARY_DOMESTIC: '일반 국내 가맹점',
  CARD_LOAN: '장기카드대출(카드론)',
  ELECTRICITY_WATER: '전기·수도요금',
  GOVERNMENT_SUBSIDY: '정부지원금',
  FUEL_STATION: '주유소',
  LOCAL_TAX: '지방세',
  CANCELLED_AMOUNT: '취소 금액',
  SIMPLE_PAY_KAKAO_ONLINE: '카카오페이 온라인',
  COFFEE_BEVERAGE: '커피·음료',
  TAXI: '택시',
  TENBYTEN: '텐바이텐',
  POINTREE_CHARGE: '포인트리 충전',
  SCHOOL_PAYMENT: '학교 납입금',
  OVERSEAS_USE: '해외 이용',
  HYPERMARKET_HOMEPLUS: '홈플러스',
  POSTPAID_TRANSIT: '후불교통',
  CGV_THEATER: 'CGV 영화관',
  CU_STORE: 'CU 편의점',
  GS25_STORE: 'GS25 편의점',
  TELECOM_KT: 'KT 통신요금',
  TELECOM_LGU: 'LG U+ 통신요금',
  TELECOM_SKT: 'SKT 통신요금',
  SIMPLE_PAY_SSG_ONLINE: 'SSG페이 온라인',
} as const

export type MerchantClass = keyof typeof merchantLabels

export function isMerchantClass(code: string): code is MerchantClass {
  return Object.hasOwn(merchantLabels, code)
}

export function merchantLabel(code: string) {
  return isMerchantClass(code) ? merchantLabels[code] : '분류 확인 필요'
}

const collator = new Intl.Collator('ko')
export const merchantClasses = (Object.keys(merchantLabels) as MerchantClass[])
  .sort((a, b) => collator.compare(merchantLabels[a], merchantLabels[b]))

export function searchMerchantClasses(query: string) {
  const normalize = (value: string) => value.toLowerCase().replace(/\s+/g, '')
  const search = normalize(query)
  return merchantClasses.filter(code =>
    normalize(merchantLabels[code]).includes(search) || normalize(code).includes(search),
  )
}
