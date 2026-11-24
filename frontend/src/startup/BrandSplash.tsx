import { useId, useState } from 'react'
import type { CSSProperties } from 'react'
import { numeral, symbolOutline } from './brandGeometry'
import { splashTiming } from './splashTiming'
import './startup.css'

export function BrandSplash({ elapsed, reduced, onExit }: {
  elapsed: number
  reduced: boolean
  onExit: () => void
}) {
  const id = useId()
  const [assetFailed, setAssetFailed] = useState(false)
  const setFailed = () => setAssetFailed(true)
  const source = '/beone-logo-blue.png'
  const clip = (name: string) => `url(#${id + name})`
  const timing = {
    '--splash-offset': `-${elapsed}ms`,
    '--splash-numeral': `${splashTiming.numeral}ms`,
    '--splash-wordmark': `${splashTiming.wordmark - splashTiming.numeral}ms`,
    '--splash-edge-light': `${splashTiming.edgeLight}ms`,
    '--splash-fade': `${splashTiming.fade}ms`,
    '--splash-fade-duration': `${splashTiming.exit - splashTiming.fade}ms`,
  } as CSSProperties

  return (
    <div className="brand-splash" data-reduced={reduced} aria-hidden="true" style={timing}
      onAnimationEnd={event => {
        if (event.target === event.currentTarget && event.animationName === 'brand-splash-exit') onExit()
      }}>
      {assetFailed ? <span className="brand-splash-fallback">B<span>:</span>ONE</span> : (
        <svg className="brand-splash-logo" viewBox="180 90 570 580" fill="none" focusable="false">
          <defs>
            <clipPath id={id + 'symbol'}><polygon points={symbolOutline} /></clipPath>
            <clipPath id={id + 'wordmark'}><rect x="180" y="530" width="570" height="140" /></clipPath>
            <clipPath id={id + 'numeral-edge'}>
              <rect x="462" y="150" width="8" height="242" rx="2" />
            </clipPath>
            <linearGradient id={id + 'edge-light'} x1="0" y1="0" x2="0" y2="1">
              <stop stopColor="#C6F0FA" stopOpacity="0" />
              <stop offset=".47" stopColor="#EFFCFF" stopOpacity=".75" />
              <stop offset=".58" stopColor="#FFFFFF" />
              <stop offset="1" stopColor="#C6F0FA" stopOpacity="0" />
            </linearGradient>
            <mask id={id + 'without-numeral'} maskUnits="userSpaceOnUse" x="0" y="0" width="900" height="760">
              <rect width="900" height="760" fill="#fff" />
              <path d={numeral} fill="#000" stroke="#000" strokeWidth="2" />
            </mask>
            <filter id={id + 'wordmark-ink'} colorInterpolationFilters="sRGB">
              <feColorMatrix type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  -15 -15 -15 0 45" />
            </filter>
          </defs>
          <image className="brand-splash-symbol-moving" href={source} width="900" height="760"
            clipPath={clip('symbol')} mask={clip('without-numeral')} onError={setFailed} />
          <image className="brand-splash-symbol-final" href={source} width="900" height="760"
            clipPath={clip('symbol')} onError={setFailed} />
          <image className="brand-splash-wordmark" href={source} width="900" height="760"
            clipPath={clip('wordmark')} filter={clip('wordmark-ink')} onError={setFailed} />
          <path className="brand-splash-numeral" d={numeral} fill="#fff" />
          {!reduced && <g clipPath={clip('numeral-edge')}>
            <rect className="brand-splash-edge-light" x="462" y="66" width="8" height="84" fill={clip('edge-light')} />
          </g>}
          {!reduced && <g className="brand-splash-sparkle-travel">
            <g className="brand-splash-sparkle">
              <path d="M465 245Q466 268 479 272Q466 276 465 299Q464 276 451 272Q464 268 465 245Z"
                fill="#C6F0FA" opacity=".55" />
              <path d="M465 249Q467 269 477 272Q467 275 465 295Q463 275 453 272Q463 269 465 249Z"
                fill="#FFFFFF" />
            </g>
          </g>}
        </svg>
      )}
    </div>
  )
}
