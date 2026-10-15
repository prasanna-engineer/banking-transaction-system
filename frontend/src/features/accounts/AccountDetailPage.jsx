import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getAccount } from '../../api/accounts'
import { searchTransactions } from '../../api/transactions'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Pager from '../../components/Pager'
import Spinner from '../../components/Spinner'
import TransactionTable from '../../components/TransactionTable'
import { formatDateTime, titleCase } from '../../utils/format'
import { formatMoney } from '../../utils/money'

const PAGE_SIZE = 10

export default function AccountDetailPage() {
  const { id } = useParams()
  const account = useApi((signal) => getAccount(id, signal), [id])

  if (account.status === 'loading') {
    return <Spinner label="Loading account…" />
  }
  if (account.status === 'error') {
    // The API answers 404 both for missing accounts and for other customers' accounts.
    const notFound = account.error.status === 404
    return (
      <>
        <h1>Account</h1>
        <Alert kind="error" onRetry={notFound ? undefined : account.reload}>
          {notFound ? 'This account was not found.' : account.error.message}
        </Alert>
        <Link to="/accounts">← Back to my accounts</Link>
      </>
    )
  }

  const a = account.data
  return (
    <>
      <Link to="/accounts" className="small">
        ← My accounts
      </Link>
      <h1>
        {titleCase(a.accountType)} account <span className="muted">{a.accountNumber}</span>
      </h1>
      <div className="cards">
        <div className="card card--highlight">
          <p className="muted small">Available balance</p>
          <p className="balance">{formatMoney(a.balance, a.currency)}</p>
          <p className="muted small">
            {titleCase(a.status)} · opened {formatDateTime(a.createdAt)}
          </p>
        </div>
      </div>
      <AccountTransactions accountId={a.id} />
    </>
  )
}

function AccountTransactions({ accountId }) {
  const [page, setPage] = useState(0)
  const history = useApi(
    (signal) => searchTransactions({ accountId, page, size: PAGE_SIZE, sort: 'createdAt,desc' }, signal),
    [accountId, page],
  )

  return (
    <section className="section" aria-labelledby="history-heading">
      <h2 id="history-heading">Transactions</h2>
      {history.status === 'loading' && <Spinner label="Loading transactions…" />}
      {history.status === 'error' && (
        <Alert kind="error" onRetry={history.reload}>
          {history.error.message}
        </Alert>
      )}
      {history.status === 'success' && history.data.content.length === 0 && (
        <div className="card empty">
          <p className="muted">No transactions for this account yet.</p>
        </div>
      )}
      {history.status === 'success' && history.data.content.length > 0 && (
        <>
          <TransactionTable
            transactions={history.data.content}
            myAccountIds={new Set([accountId])}
            caption="Transactions for this account"
          />
          <Pager pageData={history.data} onPageChange={setPage} newerOlder />
        </>
      )}
    </section>
  )
}
