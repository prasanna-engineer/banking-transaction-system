import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listAccounts } from '../../api/accounts'
import { ApiError } from '../../api/client'
import { listPayees } from '../../api/payees'
import { createTransfer } from '../../api/transfers'
import TransferPage from './TransferPage'

// Replace the real HTTP calls with fakes so the page can be tested without a backend.
vi.mock('../../api/accounts', () => ({ listAccounts: vi.fn() }))
vi.mock('../../api/payees', () => ({ listPayees: vi.fn(() => Promise.resolve([])) }))
vi.mock('../../api/transfers', () => ({
  createTransfer: vi.fn(),
  newIdempotencyKey: vi.fn(() => `key-${Math.random()}`),
}))

const ACCOUNTS = [
  { id: 1, accountNumber: '100000000001', accountType: 'SAVINGS', balance: '500.00', currency: 'INR', status: 'ACTIVE' },
  { id: 2, accountNumber: '100000000002', accountType: 'CURRENT', balance: '50.00', currency: 'INR', status: 'ACTIVE' },
]

function renderPage() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <TransferPage />
    </MemoryRouter>,
  )
}

async function fillAndReview({ amount = '25.50' } = {}) {
  await screen.findByLabelText('From')
  fireEvent.change(screen.getByLabelText('From'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('Destination account'), { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText('Amount (INR)'), { target: { value: amount } })
  fireEvent.click(screen.getByRole('button', { name: 'Review transfer' }))
}

describe('TransferPage', () => {
  beforeEach(() => {
    listAccounts.mockResolvedValue(ACCOUNTS)
  })

  it('rejects invalid amounts before anything is sent', async () => {
    renderPage()
    await fillAndReview({ amount: '10.005' })

    expect(await screen.findByText(/at most 2 decimals/)).toBeInTheDocument()
    expect(createTransfer).not.toHaveBeenCalled()
  })

  it('sends the amount as a string and shows the reference after confirming', async () => {
    createTransfer.mockResolvedValue({
      reference: 'TRX-20261015-ABCDEFGHJK',
      status: 'COMPLETED',
      sourceAccountId: 1,
      destinationAccountId: 2,
      amount: '25.50',
      currency: 'INR',
    })
    renderPage()
    await fillAndReview()

    expect(screen.getByText('Please check the details')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))

    expect(await screen.findByText('TRX-20261015-ABCDEFGHJK')).toBeInTheDocument()
    const [body] = createTransfer.mock.calls[0]
    expect(body).toEqual({ sourceAccountId: 1, destinationAccountId: 2, amount: '25.50' })
  })

  it('pays a saved payee by account number', async () => {
    listPayees.mockResolvedValue([
      { id: 7, nickname: 'Ravi (rent)', accountNumber: '100000000003', maskedAccountNumber: '••••0003' },
    ])
    createTransfer.mockResolvedValue({ reference: 'TRX-P', status: 'COMPLETED', sourceAccountId: 1, amount: '10.00', currency: 'INR' })
    renderPage()
    await screen.findByLabelText('From')
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '1' } })
    fireEvent.click(screen.getByLabelText('A saved payee'))
    fireEvent.change(await screen.findByLabelText('Payee'), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText('Amount (INR)'), { target: { value: '10' } })
    fireEvent.click(screen.getByRole('button', { name: 'Review transfer' }))

    expect(screen.getByText('Ravi (rent) (••••0003)')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))
    await screen.findByText('TRX-P')
    expect(createTransfer.mock.calls[0][0]).toEqual({ sourceAccountId: 1, destinationAccountNumber: '100000000003', amount: '10' })
  })

  it('retries a failed confirm with the SAME idempotency key, so it can never be applied twice', async () => {
    createTransfer
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server.'))
      .mockResolvedValueOnce({ reference: 'TRX-OK', status: 'COMPLETED', sourceAccountId: 1, amount: '25.50', currency: 'INR' })
    renderPage()
    await fillAndReview()

    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))
    expect(await screen.findByText('Cannot reach the server.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))

    await waitFor(() => expect(createTransfer).toHaveBeenCalledTimes(2))
    const firstKey = createTransfer.mock.calls[0][1]
    const secondKey = createTransfer.mock.calls[1][1]
    expect(firstKey).toBeTruthy()
    expect(secondKey).toBe(firstKey)
  })

  it('uses a new key after the details are edited (it is then a different request)', async () => {
    createTransfer.mockRejectedValue(new ApiError(400, 'INSUFFICIENT_FUNDS', 'The source account has insufficient funds.'))
    renderPage()
    await fillAndReview()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))
    await screen.findByText('The source account has insufficient funds.')

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.change(screen.getByLabelText('Amount (INR)'), { target: { value: '5.00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Review transfer' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm transfer' }))

    await waitFor(() => expect(createTransfer).toHaveBeenCalledTimes(2))
    expect(createTransfer.mock.calls[1][1]).not.toBe(createTransfer.mock.calls[0][1])
  })
})
