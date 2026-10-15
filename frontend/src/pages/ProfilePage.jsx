import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Alert from '../components/Alert'
import { useAuth } from '../features/auth/AuthContext'
import { formatDateTime, titleCase } from '../utils/format'

export default function ProfilePage() {
  const { user, logout, logoutEverywhere } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function handleSignOutEverywhere() {
    if (!window.confirm('Sign out on every device and browser where you are signed in?')) return
    setBusy(true)
    setError(null)
    try {
      await logoutEverywhere()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <>
      <h1>Profile</h1>
      <div className="card">
        <dl className="details">
          <dt>Name</dt>
          <dd>{user.fullName}</dd>
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Role</dt>
          <dd>{titleCase(user.role)}</dd>
          <dt>Status</dt>
          <dd>{titleCase(user.status)}</dd>
          <dt>Member since</dt>
          <dd>{formatDateTime(user.createdAt)}</dd>
        </dl>
        <div className="button-row">
          <button
            type="button"
            className="button"
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
          >
            Sign out
          </button>
        </div>
      </div>

      <section className="section" aria-labelledby="security-heading">
        <h2 id="security-heading">Security</h2>
        <div className="card">
          <p>
            Lost a device or used a shared computer? Signing out everywhere ends every session at once - including
            this one.
          </p>
          {error && <Alert kind="error">{error}</Alert>}
          <button type="button" className="button button--primary" onClick={handleSignOutEverywhere} disabled={busy}>
            {busy ? 'Signing out…' : 'Sign out of all devices'}
          </button>
        </div>
      </section>
    </>
  )
}
