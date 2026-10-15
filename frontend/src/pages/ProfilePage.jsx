import { useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { formatDateTime, titleCase } from '../utils/format'

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

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
    </>
  )
}
