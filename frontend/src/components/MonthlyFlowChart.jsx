import { useEffect, useRef, useState } from 'react'
import { formatMoney } from '../utils/money'

// Drawn at the real pixel width of the card (measured), so text stays 12px instead of being scaled up.
const DEFAULT_WIDTH = 640
const HEIGHT = 240
const MARGIN = { top: 12, right: 8, bottom: 30, left: 56 }
const PLOT_H = HEIGHT - MARGIN.top - MARGIN.bottom
const MAX_BAR = 24 // thin bars: never fill the whole slot
const GAP = 2 // surface gap between the two bars of a month

const SERIES = [
  { key: 'moneyIn', label: 'Money in', className: 'series-in' },
  { key: 'moneyOut', label: 'Money out', className: 'series-out' },
]

/** A tidy axis maximum and step: 1, 2, 2.5 or 5 times a power of ten. */
function niceScale(max) {
  if (max <= 0) return { top: 1, step: 0.25 }
  const rough = max / 4
  const power = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * power).find((s) => s >= rough)
  return { top: Math.ceil(max / step) * step, step }
}

const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 })
const monthName = (ym, style = 'short') =>
  new Date(`${ym}-01T00:00:00Z`).toLocaleString('en-IN', { month: style, year: style === 'long' ? 'numeric' : undefined, timeZone: 'UTC' })

/** Column with a 4px rounded top and a square base on the baseline. */
function barPath(x, y, w, h) {
  if (h <= 0) return ''
  const r = Math.min(4, w / 2, h)
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`
}

/**
 * Money in vs money out per month (grouped columns). Amounts arrive as exact decimal strings; they are
 * converted to numbers ONLY to compute pixel positions - every number shown is formatted from the string.
 */
export default function MonthlyFlowChart({ months, currency }) {
  const [active, setActive] = useState(null)
  const [plotRef, WIDTH] = useWidth(DEFAULT_WIDTH)
  const PLOT_W = WIDTH - MARGIN.left - MARGIN.right
  const values = months.flatMap((m) => [Number(m.moneyIn), Number(m.moneyOut)])
  const { top, step } = niceScale(Math.max(...values))
  const yOf = (value) => MARGIN.top + PLOT_H - (Number(value) / top) * PLOT_H
  const band = PLOT_W / months.length
  const barW = Math.min(MAX_BAR, (band * 0.6 - GAP) / 2)
  const ticks = Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step)
  const nothingMoved = values.every((v) => v === 0)

  return (
    <figure className="chart">
      <div className="chart__legend" aria-hidden="true">
        {SERIES.map((s) => (
          <span key={s.key} className="chart__legend-item">
            <span className={`chart__swatch ${s.className}`} />
            {s.label}
          </span>
        ))}
      </div>

      <div className="chart__plot" ref={plotRef}>
        <svg width={WIDTH} height={HEIGHT} viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`Money in and out per month, ${currency}`}>
          {ticks.map((t) => (
            <g key={t}>
              <line
                className={t === 0 ? 'chart__baseline' : 'chart__grid'}
                x1={MARGIN.left}
                x2={WIDTH - MARGIN.right}
                y1={yOf(t)}
                y2={yOf(t)}
              />
              <text className="chart__axis" x={MARGIN.left - 8} y={yOf(t) + 4} textAnchor="end">
                {t === 0 ? '0' : `₹${compact.format(t)}`}
              </text>
            </g>
          ))}

          {months.map((m, i) => {
            const x0 = MARGIN.left + i * band
            const groupX = x0 + (band - (barW * 2 + GAP)) / 2
            return (
              <g key={m.month}>
                {active === i && <rect className="chart__hover-band" x={x0} y={MARGIN.top} width={band} height={PLOT_H} />}
                {SERIES.map((s, j) => {
                  const y = yOf(m[s.key])
                  return (
                    <path
                      key={s.key}
                      className={s.className}
                      d={barPath(groupX + j * (barW + GAP), y, barW, MARGIN.top + PLOT_H - y)}
                    />
                  )
                })}
                <text className="chart__axis" x={x0 + band / 2} y={HEIGHT - 10} textAnchor="middle">
                  {monthName(m.month)}
                </text>
                {/* Hit target: the whole month column, larger than the bars, also reachable by keyboard. */}
                <rect
                  className="chart__hit"
                  x={x0}
                  y={MARGIN.top}
                  width={band}
                  height={PLOT_H}
                  tabIndex={0}
                  aria-label={`${monthName(m.month, 'long')}: in ${formatMoney(m.moneyIn, currency)}, out ${formatMoney(m.moneyOut, currency)}`}
                  onMouseEnter={() => setActive(i)}
                  onMouseLeave={() => setActive(null)}
                  onFocus={() => setActive(i)}
                  onBlur={() => setActive(null)}
                />
              </g>
            )
          })}
        </svg>

        {active !== null && (
          <div
            className="chart__tooltip"
            style={{ left: `${((MARGIN.left + (active + 0.5) * band) / WIDTH) * 100}%` }}
            role="presentation"
          >
            <strong>{monthName(months[active].month, 'long')}</strong>
            {SERIES.map((s) => (
              <span key={s.key} className="chart__tooltip-row">
                <span className={`chart__swatch ${s.className}`} />
                {s.label}
                <b>{formatMoney(months[active][s.key], currency)}</b>
              </span>
            ))}
          </div>
        )}

        {nothingMoved && <p className="chart__empty">No money moved in this period.</p>}
      </div>

      <details className="chart__table">
        <summary>Show as table</summary>
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Month</th>
              <th scope="col" className="num">
                Money in
              </th>
              <th scope="col" className="num">
                Money out
              </th>
            </tr>
          </thead>
          <tbody>
            {months.map((m) => (
              <tr key={m.month}>
                <td>{monthName(m.month, 'long')}</td>
                <td className="num">{formatMoney(m.moneyIn, currency)}</td>
                <td className="num">{formatMoney(m.moneyOut, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}

/** Current width of an element; falls back to a default where ResizeObserver is missing (tests, old browsers). */
function useWidth(fallback) {
  const ref = useRef(null)
  const [width, setWidth] = useState(fallback)
  useEffect(() => {
    if (!ref.current || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(320, Math.round(entry.contentRect.width))))
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}
