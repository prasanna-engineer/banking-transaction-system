import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../features/auth/AuthContext'
import HomePage from './HomePage'

function renderHome() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <HomePage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('HomePage', () => {
  it('invites a visitor to sign in or open an account', () => {
    renderHome()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/simple, safe and instant/)
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute('href', '/login')
    expect(screen.getByRole('link', { name: 'Get started' })).toHaveAttribute('href', '/register')
  })

  it('lists the featured services and the security section', () => {
    renderHome()

    expect(screen.getByRole('heading', { name: 'Featured services' })).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(4)
    expect(screen.getByRole('heading', { name: 'Built to keep your money safe' })).toBeInTheDocument()
  })

  it('makes clear it is not a real bank', () => {
    renderHome()
    expect(screen.getByText(/not affiliated with any real bank/)).toBeInTheDocument()
  })
})
