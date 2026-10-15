import { Outlet } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Hides pages from users without the right role. UI convenience only: the backend independently
 * answers 403 for admin endpoints and for transfers by non-customers.
 */
export default function RoleRoute({ role }) {
  const { user } = useAuth()
  if (user?.role !== role) {
    return (
      <div className="card empty">
        <h1>Not available</h1>
        <p className="muted">This page is only for {role.toLowerCase()} users.</p>
      </div>
    )
  }
  return <Outlet />
}
