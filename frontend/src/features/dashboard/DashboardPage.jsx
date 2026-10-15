import { Link } from 'react-router-dom'
import { listAccounts } from '../../api/accounts'
import { searchTransactions } from '../../api/transactions'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import TransactionTable from '../../components/TransactionTable'
import { useAuth } from '../auth/AuthContext'
import { maskAccountNumber, titleCase } from '../../utils/format'
import { formatMoney, sumAmounts } from '../../utils/money'

export default function DashboardPage() {
  const { user } = useAuth()
  // Both requests start together; the page waits for both.
  const { status, data, error, reload } = useApi(
    (signal) =>
      Promise.all([listAccounts(signal), searchTransactions({ size: 5, sort: 'createdAt,desc' }, signal)]),
    [],
  )

  return (
    <>
      <h1>Hello, {user?.fullName?.split(' ')[0]}</h1>
      {status === 'loading' && <Spinner label="Loading your accounts…" />}
      {status === 'error' && (
        <Alert kind="error" onRetry={reload}>
          {error.message}
        </Alert>
      )}
      {status === 'success' && <DashboardContent accounts={data[0]} recent={data[1].content} />}
    </>
  )
}

function DashboardContent({ accounts, recent }) {
  if (accounts.length === 0) {
    return (
      <div className="card empty">
        <h2>No accounts yet</h2>
        <p className="muted">Demo accounts are created by the administrator.</p>
      </div>
    )
  }

  // Totals per currency, added exactly (see utils/money.js). Never mix currencies in one total.
  const totals = Object.entries(
    accounts.reduce((groups, account) => {
      ;(groups[account.currency] ??= []).push(account.balance)
      return groups
    }, {}),
  ).map(([currency, balances]) => ({ currency, total: sumAmounts(balances) }))
  const myAccountIds = new Set(accounts.map((a) => a.id))

  return (
    <>
      <section aria-labelledby="summary-heading">
        <h2 id="summary-heading" className="visually-hidden">
          Account summary
        </h2>
        <div className="cards">
          {totals.map(({ currency, total }) => (
            <div className="card card--highlight" key={currency}>
              <p className="muted small">Total balance ({currency})</p>
              <p className="balance">{formatMoney(total, currency)}</p>
              <p className="muted small">
                across {accounts.length} account{accounts.length === 1 ? '' : 's'}
              </p>
            </div>
          ))}
          {accounts.map((account) => (
            <Link to={`/accounts/${account.id}`} className="card card--link" key={account.id}>
              <p className="muted small">
                {titleCase(account.accountType)} · {maskAccountNumber(account.accountNumber)}
              </p>
              <p className="balance">{formatMoney(account.balance, account.currency)}</p>
              <span className="badge">{titleCase(account.status)}</span>
            </Link>
          ))}
        </div>
      </section>

      <section aria-labelledby="recent-heading" className="section">
        <div className="section__header">
          <h2 id="recent-heading">Recent transactions</h2>
        </div>
        {recent.length === 0 ? (
          <div className="card empty">
            <p className="muted">No transactions yet.</p>
          </div>
        ) : (
          <TransactionTable transactions={recent} myAccountIds={myAccountIds} caption="Recent transactions" />
        )}
      </section>
    </>
  )
}
