import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listAccounts } from '../../api/accounts'
import { downloadStatement, searchTransactions } from '../../api/transactions'
import { useApi } from '../../api/useApi'
import { useLive } from '../notifications/LiveContext'
import Alert from '../../components/Alert'
import Pager from '../../components/Pager'
import Spinner from '../../components/Spinner'
import TransactionTable from '../../components/TransactionTable'
import { maskAccountNumber, titleCase } from '../../utils/format'

const FILTER_KEYS = ['accountId', 'type', 'status', 'from', 'to', 'sort', 'size']
const DEFAULTS = { sort: 'createdAt,desc', size: '20' }

/** Reads the current filters from the URL query string, e.g. ?type=TRANSFER&from=2026-10-01&page=1 */
function filtersFromUrl(searchParams) {
  return Object.fromEntries(FILTER_KEYS.map((key) => [key, searchParams.get(key) ?? DEFAULTS[key] ?? '']))
}

export default function HistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { moneyVersion } = useLive()
  const applied = filtersFromUrl(searchParams)
  const page = Number(searchParams.get('page') ?? 0)

  const [draft, setDraft] = useState(applied)
  const [formError, setFormError] = useState(null)
  const [downloading, setDownloading] = useState(false)

  const accounts = useApi((signal) => listAccounts(signal), [])
  const history = useApi(
    (signal) => searchTransactions({ ...applied, page }, signal),
    [searchParams.toString(), moneyVersion],
  )

  function update(field) {
    return (event) => setDraft((previous) => ({ ...previous, [field]: event.target.value }))
  }

  function writeUrl(filters, newPage) {
    const next = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => {
      if (value && value !== DEFAULTS[key]) next.set(key, value)
    })
    if (newPage > 0) next.set('page', String(newPage))
    setSearchParams(next)
  }

  function handleApply(event) {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) {
      setFormError("'From' must be on or before 'To'.")
      return
    }
    setFormError(null)
    writeUrl(draft, 0) // new filters always start again at the first page
  }

  // Exports exactly what the applied filters show (all pages, newest first).
  async function handleDownload() {
    setDownloading(true)
    setFormError(null)
    try {
      await downloadStatement(applied)
    } catch (err) {
      setFormError(err.message)
    } finally {
      setDownloading(false)
    }
  }

  function handleReset() {
    const cleared = filtersFromUrl(new URLSearchParams())
    setDraft(cleared)
    setFormError(null)
    setSearchParams(new URLSearchParams())
  }

  const myAccounts = accounts.data ?? []
  const myAccountIds = new Set(myAccounts.map((a) => a.id))

  return (
    <>
      <div className="section__header">
        <h1>Transactions</h1>
        <button type="button" className="button" onClick={handleDownload} disabled={downloading}>
          {downloading ? 'Preparing…' : 'Download CSV'}
        </button>
      </div>

      <form className="card filters" onSubmit={handleApply} aria-label="Filter transactions">
        {myAccounts.length > 0 && (
          <div className="field">
            <label htmlFor="accountId">Account</label>
            <select id="accountId" value={draft.accountId} onChange={update('accountId')}>
              <option value="">All my accounts</option>
              {myAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {titleCase(a.accountType)} {maskAccountNumber(a.accountNumber)}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="field">
          <label htmlFor="type">Type</label>
          <select id="type" value={draft.type} onChange={update('type')}>
            <option value="">Any</option>
            <option value="TRANSFER">Transfer</option>
            <option value="DEPOSIT">Deposit</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="status">Status</label>
          <select id="status" value={draft.status} onChange={update('status')}>
            <option value="">Any</option>
            <option value="COMPLETED">Completed</option>
            <option value="PENDING">Pending</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="from">From</label>
          <input id="from" type="date" value={draft.from} onChange={update('from')} />
        </div>
        <div className="field">
          <label htmlFor="to">To</label>
          <input id="to" type="date" value={draft.to} onChange={update('to')} />
        </div>
        <div className="field">
          <label htmlFor="sort">Sort by</label>
          <select id="sort" value={draft.sort} onChange={update('sort')}>
            <option value="createdAt,desc">Newest first</option>
            <option value="createdAt,asc">Oldest first</option>
            <option value="amount,desc">Largest amount</option>
            <option value="amount,asc">Smallest amount</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="size">Per page</label>
          <select id="size" value={draft.size} onChange={update('size')}>
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
          </select>
        </div>
        <div className="filters__actions">
          <button type="submit" className="button button--primary">
            Apply
          </button>
          <button type="button" className="button button--ghost" onClick={handleReset}>
            Reset
          </button>
        </div>
      </form>

      {formError && <Alert kind="error">{formError}</Alert>}

      <section className="section" aria-live="polite">
        {history.status === 'loading' && <Spinner label="Loading transactions…" />}
        {history.status === 'error' && (
          <Alert kind="error" onRetry={history.reload}>
            {history.error.message}
          </Alert>
        )}
        {history.status === 'success' && history.data.content.length === 0 && (
          <div className="card empty">
            <p className="muted">No transactions match these filters.</p>
          </div>
        )}
        {history.status === 'success' && history.data.content.length > 0 && (
          <>
            <TransactionTable
              transactions={history.data.content}
              myAccountIds={myAccountIds}
              caption="Transaction history"
            />
            <Pager pageData={history.data} onPageChange={(p) => writeUrl(applied, p)} />
          </>
        )}
      </section>
    </>
  )
}
