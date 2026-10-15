import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MonthlyFlowChart from './MonthlyFlowChart'

const MONTHS = [
  { month: '2026-09', moneyIn: '12500.50', moneyOut: '0.00' },
  { month: '2026-10', moneyIn: '1250.50', moneyOut: '2100.25' },
]

describe('MonthlyFlowChart', () => {
  it('draws one bar per series per month', () => {
    const { container } = render(<MonthlyFlowChart months={MONTHS} currency="INR" />)
    expect(container.querySelectorAll('path.series-in')).toHaveLength(2)
    expect(container.querySelectorAll('path.series-out')).toHaveLength(2)
  })

  it('shows exact amounts in a tooltip on hover and in the keyboard label', () => {
    render(<MonthlyFlowChart months={MONTHS} currency="INR" />)
    const october = screen.getByLabelText('October 2026: in ₹1,250.50, out ₹2,100.25')

    fireEvent.mouseEnter(october)
    const tooltip = screen.getByText('October 2026', { selector: '.chart__tooltip strong' }).closest('.chart__tooltip')
    expect(within(tooltip).getByText('₹2,100.25')).toBeInTheDocument()
  })

  it('offers the same numbers as a table', () => {
    render(<MonthlyFlowChart months={MONTHS} currency="INR" />)
    const table = screen.getByRole('table')
    expect(within(table).getByText('₹12,500.50')).toBeInTheDocument()
  })

  it('says so when nothing moved', () => {
    render(<MonthlyFlowChart months={[{ month: '2026-10', moneyIn: '0.00', moneyOut: '0.00' }]} currency="INR" />)
    expect(screen.getByText('No money moved in this period.')).toBeInTheDocument()
  })
})
