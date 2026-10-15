import { describe, expect, it } from 'vitest'
import { maskAccountNumber, titleCase } from './format'

describe('maskAccountNumber', () => {
  it('shows only the last four digits', () => {
    expect(maskAccountNumber('100000000003')).toBe('•••• 0003')
  })

  it('returns an empty string for a missing number', () => {
    expect(maskAccountNumber(null)).toBe('')
  })
})

describe('titleCase', () => {
  it('turns API enum values into readable labels', () => {
    expect(titleCase('SAVINGS')).toBe('Savings')
    expect(titleCase('')).toBe('')
  })
})
