import { describe, expect, it } from 'vitest'
import { formatMoney, sumAmounts } from './money'

describe('sumAmounts', () => {
  it('adds decimal strings exactly, unlike JavaScript numbers', () => {
    expect(0.1 + 0.2).not.toBe(0.3) // the reason this helper exists
    expect(sumAmounts(['0.10', '0.20'])).toBe('0.30')
  })

  it('adds real balances', () => {
    expect(sumAmounts(['49900.00', '12500.50'])).toBe('62400.50')
  })

  it('stays exact beyond the range where floats lose cents', () => {
    expect(sumAmounts(['999999999999999.99', '0.01'])).toBe('1000000000000000.00')
  })

  it('handles an empty list and negative values', () => {
    expect(sumAmounts([])).toBe('0.00')
    expect(sumAmounts(['10.00', '-2.50'])).toBe('7.50')
  })
})

describe('formatMoney', () => {
  it('uses Indian digit grouping and always two decimals', () => {
    expect(formatMoney('1250000.5', 'INR')).toBe('₹12,50,000.50')
    expect(formatMoney('0', 'INR')).toBe('₹0.00')
  })

  it('shows negative amounts with a minus sign', () => {
    expect(formatMoney('-25.5', 'INR')).toBe('−₹25.50')
  })

  it('uses the right symbol for other currencies', () => {
    expect(formatMoney('10', 'USD')).toBe('$10.00')
  })
})
