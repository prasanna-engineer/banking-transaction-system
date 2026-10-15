import { useState } from 'react'
import { changeUserStatus, listUsers } from '../../api/admin'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Pager from '../../components/Pager'
import Spinner from '../../components/Spinner'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime, titleCase } from '../../utils/format'

export default function AdminUsersPage() {
  const { user: me } = useAuth()
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState({ q: '', page: 0 })
  const [busyUserId, setBusyUserId] = useState(null)
  const [message, setMessage] = useState(null)

  const users = useApi((signal) => listUsers({ q: query.q, page: query.page, size: 20 }, signal), [query.q, query.page])

  async function toggleStatus(user) {
    const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    const verb = next === 'DISABLED' ? 'Disable' : 'Enable'
    if (!window.confirm(`${verb} ${user.email}? This is recorded in the audit log.`)) return
    setBusyUserId(user.id)
    setMessage(null)
    try {
      const updated = await changeUserStatus(user.id, next)
      setMessage({ kind: 'success', text: `${updated.email} is now ${titleCase(updated.status)}.` })
      users.reload()
    } catch (err) {
      setMessage({ kind: 'error', text: err.message })
    } finally {
      setBusyUserId(null)
    }
  }

  return (
    <>
      <h1>Users</h1>
      <form
        className="card filters"
        onSubmit={(e) => {
          e.preventDefault()
          setQuery({ q: search.trim(), page: 0 })
        }}
        role="search"
      >
        <div className="field">
          <label htmlFor="q">Search name or email</label>
          <input id="q" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="filters__actions">
          <button type="submit" className="button button--primary">
            Search
          </button>
        </div>
      </form>

      {message && <Alert kind={message.kind}>{message.text}</Alert>}

      <section className="section">
        {users.status === 'loading' && !users.data && <Spinner label="Loading users…" />}
        {users.status === 'error' && (
          <Alert kind="error" onRetry={users.reload}>
            {users.error.message}
          </Alert>
        )}
        {users.data && users.data.content.length === 0 && (
          <div className="card empty">
            <p className="muted">No users found.</p>
          </div>
        )}
        {users.data && users.data.content.length > 0 && (
          <>
            <div className="table-wrap">
              <table className="table">
                <caption className="visually-hidden">Demo users</caption>
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Email</th>
                    <th scope="col">Role</th>
                    <th scope="col">Status</th>
                    <th scope="col">Joined</th>
                    <th scope="col">
                      <span className="visually-hidden">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.content.map((u) => (
                    <tr key={u.id}>
                      <td>{u.fullName}</td>
                      <td>{u.email}</td>
                      <td>{titleCase(u.role)}</td>
                      <td>
                        <span className={`badge badge--${u.status.toLowerCase()}`}>{titleCase(u.status)}</span>
                      </td>
                      <td>{formatDateTime(u.createdAt)}</td>
                      <td className="num">
                        {u.id === me.id ? (
                          <span className="muted small">You</span>
                        ) : (
                          <button
                            type="button"
                            className="button button--small"
                            disabled={busyUserId === u.id}
                            onClick={() => toggleStatus(u)}
                          >
                            {u.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager pageData={users.data} onPageChange={(p) => setQuery((q) => ({ ...q, page: p }))} />
          </>
        )}
      </section>
    </>
  )
}
