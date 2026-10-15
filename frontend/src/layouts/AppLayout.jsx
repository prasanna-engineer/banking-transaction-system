import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

// roles: who sees the link. Hiding a link is only convenience - the API enforces the real rules.
const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/accounts', label: 'My accounts', roles: ['CUSTOMER'] },
  { to: '/transfer', label: 'Transfer', roles: ['CUSTOMER'] },
  { to: '/transactions', label: 'Transactions' },
  { to: '/admin/users', label: 'Users', roles: ['ADMIN'] },
  { to: '/admin/audit-logs', label: 'Audit log', roles: ['ADMIN'] },
  { to: '/profile', label: 'Profile' },
]

/** Left navigation rail + top header around every logged-in page (like a Blade layout with @yield). */
export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleSignOut() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className="shell__nav" aria-label="Main navigation">
        <div className="brand">
          Demo<span>Bank</span>
        </div>
        <nav>
          <ul className="nav-list">
            {NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(user?.role)).map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className="nav-link">
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <p className="demo-note">Demo app · fictional data · no real money</p>
      </aside>
      <div className="shell__body">
        <header className="topbar">
          <span className="topbar__user">
            {user?.fullName}
            <span className="badge">{user?.role}</span>
          </span>
          <button type="button" className="button button--ghost" onClick={handleSignOut}>
            Sign out
          </button>
        </header>
        <main id="main" className="content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
