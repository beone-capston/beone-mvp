import { useState } from 'react'

const cards = [
  ['올라운드 티타늄카드', '신용', 'titanium'],
  ['청춘대로 톡톡카드', '신용', 'coral'],
  ['노리 체크카드', '체크', 'lime'],
  ['첵첵 체크카드', '체크', 'violet'],
]
function Brand() {
  return <span className="brand"><span className="brand-symbol"><img src="/beone-brand.png" alt="" /></span><span>B<span className="mint">:</span>ONE</span></span>
}
function App() {
  const [demo, setDemo] = useState(false)
  const [notice, setNotice] = useState('')
  function comingSoon(name: string) { setNotice(name + ' 기능은 준비 중이에요. 지금은 메인화면을 둘러볼 수 있어요.') }
  function selectMode(value: boolean) { setDemo(value); setNotice('') }
  return (
    <div className="app-shell">
      <header><a href="#main" aria-label="B:ONE 홈"><Brand /></a><span className="badge">● 시뮬레이션</span></header>
      <main id="main">
        <section className="welcome"><div><p className="eyebrow">나의 똑똑한 카드 생활</p><h1>모든 결제에,<br className="mobile-break" /> 더 현명한 한 장.</h1><p className="description">오늘의 결제도 B:ONE과 함께 준비하세요.</p></div></section>
        <div className="mode-switch" role="group" aria-label="포트폴리오 모드"><button aria-pressed={!demo} onClick={() => selectMode(false)}>내 카드</button><button aria-pressed={demo} onClick={() => selectMode(true)}>가상 포트폴리오 <small>체험</small></button></div>
        <div className="dashboard">
          <section className="hero" aria-labelledby="hero-title"><span className="kicker">YOUR ONE CARD</span><h2 id="hero-title">나의 B:ONE 가상카드</h2><p>결제 전, 내 카드의 혜택을 한곳에서.</p><div className="virtual-card" aria-label="B:ONE 가상카드 디자인"><div className="card-brand">B<span>:</span>ONE <small>VIRTUAL CARD</small></div><div className="card-art" aria-hidden="true"><i /><i /><i /></div><div className="card-bottom">ONE STEP AHEAD <span>◇</span></div></div><div className="hero-footer"><span>● 실제 결제 없는 가상카드</span></div></section>
          <section className="start panel" aria-labelledby="start-title"><span className="kicker">LET’S GET STARTED</span><h2 id="start-title">{demo ? '내 카드 없이도, 가볍게 체험' : '현명한 결제의 시작'}</h2><p>{demo ? '가상의 카드 4종으로 서비스 흐름을 살펴보세요.' : '카드와 월간 상태를 입력하고 준비해보세요.'}</p><ol className="steps"><li><span>01</span><div><strong>카드와 월간 상태 확인</strong><p>보유 카드 · 전월 실적 · 혜택 사용액</p></div></li><li><span>02</span><div><strong>가상 주문 만들기</strong><p>가맹점과 금액을 자유롭게 입력</p></div></li><li><span>03</span><div><strong>추천 확인 후 모의 승인</strong><p>계산 근거와 카드별 예상 혜택 비교</p></div></li></ol><button className="primary" onClick={() => comingSoon('가상 주문 만들기')}>가상 주문 만들기 <span aria-hidden="true">↗</span></button><small className="hint">주문 편집 기능은 준비 중이에요</small></section>
          <section className="portfolio panel" aria-labelledby="portfolio-title"><div className="section-heading"><div><span className="kicker">MY PORTFOLIO</span><h2 id="portfolio-title">{demo ? '가상 포트폴리오' : '내 카드'} <span className="count">{demo ? 4 : 0}</span></h2></div><button className="text-button" onClick={() => comingSoon('카드 관리')}>{demo ? '카드 살펴보기' : '카드 등록'} <span aria-hidden="true">→</span></button></div>{demo ? <><p className="demo-notice">예시 데이터 · 실제 보유 카드가 아닌 시연용 구성입니다.</p><ul className="demo-cards">{cards.map(([name, type, color]) => <li key={name}><span className={'mini-card ' + color} aria-hidden="true">B:1</span><div><small>{type}</small><strong>{name}</strong><span className="unknown">월간 상태 확인 필요</span></div></li>)}</ul></> : <div className="empty"><span className="empty-icon" aria-hidden="true">▱</span><h3>첫 번째 카드를 기다리고 있어요</h3><p>카드를 등록하면 나의 포트폴리오를 확인할 수 있어요.<br />등록 전에는 가상 포트폴리오를 둘러보세요.</p><button className="secondary" onClick={() => selectMode(true)}>가상 포트폴리오 체험 <span aria-hidden="true">→</span></button></div>}</section>
          <section className="insight" aria-labelledby="state-title"><span className="insight-icon" aria-hidden="true">◇</span><span className="kicker">A CLEARER PICTURE</span><h2 id="state-title">정확한 혜택은<br />정확한 상태에서부터.</h2><p>전월 실적과 이번 달 혜택 사용액을 확인해주세요. 모르는 값은 <strong>‘확인 필요’</strong>로 구분해요.</p><button className="text-button" onClick={() => comingSoon('월간 상태 확인')}>월간 상태 확인 <span aria-hidden="true">→</span></button></section>
        </div>
        <div className="service-notice"><span aria-hidden="true">◇</span><p>B:ONE은 카드 혜택 시뮬레이터입니다. <strong>실제 결제 및 금융거래는 발생하지 않습니다.</strong><br />예상 혜택은 입력한 상태와 적용 규칙에 따라 달라질 수 있습니다.</p></div>
        {notice && <div className="notice" role="status"><span>{notice}</span><button aria-label="안내 닫기" onClick={() => setNotice('')}>×</button></div>}
      </main>
      <footer><small>© 2026 BeONE · ONE STEP AHEAD</small></footer>
      <nav className="mobile-nav" aria-label="주요 메뉴"><a href="#main" aria-current="page"><span aria-hidden="true">⌂</span>홈</a><button onClick={() => comingSoon('카드 관리')}><span aria-hidden="true">▱</span>카드</button><button onClick={() => comingSoon('가상 주문 만들기')}><span aria-hidden="true">☷</span>가상 주문</button></nav>
    </div>
  )
}
export default App
