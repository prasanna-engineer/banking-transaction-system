import { Link } from 'react-router-dom'
import { listAccounts } from '../../api/accounts'
import { getMonthlyFlow } from '../../api/insights'
import { searchTransactions } from '../../api/transactions'
import { useApi } from '../../api/useApi'
import { useLive } from '../notifications/LiveContext'
import Alert from '../../components/Alert'
import MonthlyFlowChart from '../../components/MonthlyFlowChart'
import Spinner from '../../components/Spinner'
import TransactionTable from '../../components/TransactionTable'
import { useAuth } from '../auth/AuthContext'
import { maskAccountNumber, titleCase } from '../../utils/format'
import { formatMoney, sumAmounts } from '../../utils/money'

export default function DashboardPage() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <AdminHome name={user.fullName} /> : <CustomerDashboard user={user} />
}

function AdminHome({ name }) {
  return (
    <>
      <h1>Hello, {name?.split(' ')[0]}</h1>
      <p className="muted">You are signed in as an administrator. Administrators inspect activity but do not move money.</p>
      <div className="cards">
        <Link to="/admin/users" className="card card--link">
          <h2>Users</h2>
          <p className="muted small">Search demo users and enable or disable them.</p>
        </Link>
        <Link to="/admin/audit-logs" className="card card--link">
          <h2>Audit log</h2>
          <p className="muted small">Who did what, and when.</p>
        </Link>
        <Link to="/transactions" className="card card--link">
          <h2>All transactions</h2>
          <p className="muted small">Every transfer and deposit in the system.</p>
        </Link>
      </div>
    </>
  )
}

function CustomerDashboard({ user }) {
  const { moneyVersion } = useLive()
  // Both requests start together; the page waits for both.
  const { status, data, error, reload } = useApi(
    (signal) =>
      Promise.all([listAccounts(signal), searchTransactions({ size: 5, sort: 'createdAt,desc' }, signal)]),
    [moneyVersion], // reload when money moves (live event)
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
        <p className="muted">No bank accounts are linked to this login yet. Try a demo customer such as asha@example.com.</p>
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

      <MoneyFlowSection />

      <section aria-labelledby="recent-heading" className="section">
        <div className="section__header">
          <h2 id="recent-heading">Recent transactions</h2>
          <div className="button-row">
            <Link to="/transfer" className="button button--primary button--small">
              New transfer
            </Link>
            <Link to="/transactions" className="button button--small">
              View all
            </Link>
          </div>
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

function MoneyFlowSection() {
  const { moneyVersion } = useLive()
  const flow = useApi((signal) => getMonthlyFlow({ months: 6 }, signal), [moneyVersion])

  return (
    <section className="section" aria-labelledby="flow-heading">
      <h2 id="flow-heading">Money in vs out · last 6 months</h2>
      <div className="card">
        {flow.status === 'loading' && <Spinner label="Loading chart…" />}
        {flow.status === 'error' && (
          <Alert kind="error" onRetry={flow.reload}>
            {flow.error.message}
          </Alert>
        )}
        {flow.status === 'success' && <MonthlyFlowChart months={flow.data.months} currency={flow.data.currency} />}
      </div>
    </section>
  )
}
