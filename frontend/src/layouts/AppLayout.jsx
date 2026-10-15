import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import BrandLogo from '../components/BrandLogo'
import SiteFooter from '../components/SiteFooter'
import { LiveProvider } from '../features/notifications/LiveContext'
import NotificationBell from '../features/notifications/NotificationBell'
import { useAuth } from '../features/auth/AuthContext'
import { titleCase } from '../utils/format'

// roles: who sees the link. Hiding a link is only convenience - the API enforces the real rules.
const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/accounts', label: 'My accounts', roles: ['CUSTOMER'] },
  { to: '/transfer', label: 'Transfer', roles: ['CUSTOMER'] },
  { to: '/payees', label: 'Payees', roles: ['CUSTOMER'] },
  { to: '/transactions', label: 'Transactions' },
  { to: '/admin/users', label: 'Users', roles: ['ADMIN'] },
  { to: '/admin/audit-logs', label: 'Audit log', roles: ['ADMIN'] },
  { to: '/profile', label: 'Profile' },
]

/** Brand header + side navigation around every logged-in page (like a Blade layout with @yield). */
export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleSignOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <LiveProvider>
      <div className="app">
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <header className="brand-header">
          <NavLink to="/" className="brand-link" aria-label="DemoBank home">
            <BrandLogo />
          </NavLink>
          <div className="brand-header__actions">
            <NotificationBell />
            <span className="brand-header__user">
              {user?.fullName}
              <span className="badge badge--on-brand">{titleCase(user?.role)}</span>
            </span>
            <button type="button" className="button button--on-brand" onClick={handleSignOut}>
              Sign out
            </button>
          </div>
        </header>

        <div className="shell">
          <nav className="shell__nav" aria-label="Main navigation">
            <ul className="nav-list">
              {NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(user?.role)).map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} className="nav-link">
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <main id="main" className="content" tabIndex={-1}>
            <Outlet />
          </main>
        </div>

        <SiteFooter />
      </div>
    </LiveProvider>
  )
}
