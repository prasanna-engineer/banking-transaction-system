import { Link } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import BrandLogo from './BrandLogo'

/** Orange gradient header for the public pages (home, sign in, register). */
export default function PublicHeader() {
  const { status } = useAuth()

  return (
    <header className="brand-header">
      <Link to="/" className="brand-link" aria-label="DemoBank home">
        <BrandLogo />
      </Link>
      <nav className="brand-header__nav" aria-label="Site">
        <a href="/#services">Services</a>
        <a href="/#security">Security</a>
      </nav>
      <div className="brand-header__actions">
        {status === 'authenticated' ? (
          <Link to="/dashboard" className="button button--on-brand">
            My dashboard
          </Link>
        ) : (
          <>
            <Link to="/register" className="button button--outline-light">
              Open account
            </Link>
            <Link to="/login" className="button button--on-brand">
              Sign in
            </Link>
          </>
        )}
      </div>
    </header>
  )
}
