import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import Spinner from '../components/Spinner'

/**
 * Only renders the nested routes for a logged-in user; otherwise sends them to /login.
 * This is a convenience for the UI - the real protection is the backend rejecting requests
 * without a valid token (hiding a page is not security).
 */
export default function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <Spinner label="Checking your session…" fullPage />
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}
