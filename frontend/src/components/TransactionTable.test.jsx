import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import TransactionTable from './TransactionTable'

function tx(overrides) {
  return {
    id: 1,
    reference: 'TRX-TEST',
    type: 'TRANSFER',
    status: 'COMPLETED',
    sourceAccountId: 1,
    maskedSourceAccountNumber: '********0001',
    destinationAccountId: 3,
    maskedDestinationAccountNumber: '********0003',
    amount: '100.00',
    currency: 'INR',
    description: 'Rent',
    createdAt: '2026-10-14T09:30:00Z',
    ...overrides,
  }
}

function amountCell(description) {
  const row = screen.getByText(description).closest('tr')
  return within(row).getByText(/₹/).closest('td')
}

describe('TransactionTable', () => {
  it('shows money leaving my account as a debit and arriving as a credit', () => {
    render(
      <TransactionTable
        myAccountIds={new Set([1])}
        transactions={[
          tx({ id: 1, description: 'Paid out' }),
          tx({ id: 2, description: 'Paid in', sourceAccountId: 3, destinationAccountId: 1 }),
        ]}
      />,
    )

    expect(amountCell('Paid out')).toHaveClass('amount--debit')
    expect(amountCell('Paid out')).toHaveTextContent('−₹100.00')
    expect(amountCell('Paid in')).toHaveClass('amount--credit')
    expect(amountCell('Paid in')).toHaveTextContent('+₹100.00')
  })

  it('marks a move between two of my own accounts as internal, without a sign', () => {
    render(<TransactionTable myAccountIds={new Set([1, 3])} transactions={[tx({ description: 'To savings' })]} />)
    expect(amountCell('To savings')).toHaveClass('amount--internal')
    expect(amountCell('To savings')).toHaveTextContent(/^internal ₹100\.00$/)
  })

  it('shows deposits with "Deposit" as the source', () => {
    render(
      <TransactionTable
        myAccountIds={new Set([1])}
        transactions={[
          tx({ type: 'DEPOSIT', description: null, sourceAccountId: null, maskedSourceAccountNumber: null, destinationAccountId: 1 }),
        ]}
      />,
    )
    expect(screen.getByText(/Deposit →/)).toBeInTheDocument()
    // No description, so the type is shown instead.
    expect(screen.getByText('Deposit', { selector: 'div' })).toBeInTheDocument()
  })

  it('shows no sign for an admin, who owns no accounts', () => {
    render(<TransactionTable myAccountIds={new Set()} transactions={[tx({ description: 'Someone else' })]} />)
    expect(amountCell('Someone else')).toHaveTextContent(/^₹100\.00$/)
  })
})
