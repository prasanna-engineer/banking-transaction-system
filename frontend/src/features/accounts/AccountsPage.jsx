import { Link } from 'react-router-dom'
import { listAccounts } from '../../api/accounts'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { formatDateTime, maskAccountNumber, titleCase } from '../../utils/format'
import { formatMoney } from '../../utils/money'

export default function AccountsPage() {
  const { status, data: accounts, error, reload } = useApi((signal) => listAccounts(signal), [])

  return (
    <>
      <h1>My accounts</h1>
      {status === 'loading' && <Spinner label="Loading accounts…" />}
      {status === 'error' && (
        <Alert kind="error" onRetry={reload}>
          {error.message}
        </Alert>
      )}
      {status === 'success' && accounts.length === 0 && (
        <div className="card empty">
          <p className="muted">You have no accounts yet.</p>
        </div>
      )}
      {status === 'success' && accounts.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <caption className="visually-hidden">Your accounts</caption>
            <thead>
              <tr>
                <th scope="col">Account</th>
                <th scope="col">Type</th>
                <th scope="col">Status</th>
                <th scope="col">Opened</th>
                <th scope="col" className="num">
                  Balance
                </th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>
                    <Link to={`/accounts/${account.id}`}>{maskAccountNumber(account.accountNumber)}</Link>
                  </td>
                  <td>{titleCase(account.accountType)}</td>
                  <td>
                    <span className="badge">{titleCase(account.status)}</span>
                  </td>
                  <td>{formatDateTime(account.createdAt)}</td>
                  <td className="num">{formatMoney(account.balance, account.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
