import { useState } from 'react'
import { listAuditLogs } from '../../api/admin'
import { useApi } from '../../api/useApi'
import { useLive } from '../notifications/LiveContext'
import Alert from '../../components/Alert'
import Pager from '../../components/Pager'
import Spinner from '../../components/Spinner'
import { formatDateTime } from '../../utils/format'

const ACTIONS = ['TRANSFER_COMPLETED', 'USER_STATUS_CHANGED']

/** Shows metadata as readable "key: value" text. Rendered as plain text, never as HTML. */
function describe(metadataJson) {
  if (!metadataJson) return '—'
  try {
    return Object.entries(JSON.parse(metadataJson))
      .map(([key, value]) => `${key}: ${value}`)
      .join(' · ')
  } catch {
    return metadataJson
  }
}

export default function AdminAuditLogsPage() {
  const { auditVersion } = useLive()
  const [filter, setFilter] = useState({ action: '', page: 0 })
  const logs = useApi(
    (signal) => listAuditLogs({ action: filter.action, page: filter.page, size: 20 }, signal),
    [filter.action, filter.page, auditVersion], // live: reload when a new audit event arrives
  )

  return (
    <>
      <h1>Audit log</h1>
      <div className="card filters">
        <div className="field">
          <label htmlFor="action">Action</label>
          <select id="action" value={filter.action} onChange={(e) => setFilter({ action: e.target.value, page: 0 })}>
            <option value="">All actions</option>
            {ACTIONS.map((action) => (
              <option key={action} value={action}>
                {action}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section className="section">
        {logs.status === 'loading' && <Spinner label="Loading audit log…" />}
        {logs.status === 'error' && (
          <Alert kind="error" onRetry={logs.reload}>
            {logs.error.message}
          </Alert>
        )}
        {logs.status === 'success' && logs.data.content.length === 0 && (
          <div className="card empty">
            <p className="muted">No audit events yet.</p>
          </div>
        )}
        {logs.status === 'success' && logs.data.content.length > 0 && (
          <>
            <div className="table-wrap">
              <table className="table">
                <caption className="visually-hidden">Audit events, newest first</caption>
                <thead>
                  <tr>
                    <th scope="col">When</th>
                    <th scope="col">Who</th>
                    <th scope="col">Action</th>
                    <th scope="col">Record</th>
                    <th scope="col">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.data.content.map((log) => (
                    <tr key={log.id}>
                      <td>{formatDateTime(log.createdAt)}</td>
                      <td>{log.actorEmail ?? 'system'}</td>
                      <td>
                        <span className="badge">{log.action}</span>
                      </td>
                      <td className="muted">
                        {log.entityType} #{log.entityId}
                      </td>
                      <td className="small">{describe(log.metadataJson)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager pageData={logs.data} onPageChange={(p) => setFilter((f) => ({ ...f, page: p }))} />
          </>
        )}
      </section>
    </>
  )
}
