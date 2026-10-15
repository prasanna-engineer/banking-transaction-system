import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createPayee, deletePayee, listPayees } from '../../api/payees'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { useToast } from '../../components/Toaster'
import { formatDateTime } from '../../utils/format'

const ACCOUNT_NUMBER_PATTERN = /^\d{6,20}$/

export default function PayeesPage() {
  const payees = useApi((signal) => listPayees(signal), [])
  const { showToast } = useToast()
  const [form, setForm] = useState({ nickname: '', accountNumber: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  async function handleAdd(event) {
    event.preventDefault()
    const errors = {}
    if (!form.nickname.trim()) errors.nickname = 'Give this payee a name you will recognise.'
    if (!ACCOUNT_NUMBER_PATTERN.test(form.accountNumber.trim())) errors.accountNumber = 'Enter the 6–20 digit account number.'
    setFieldErrors(errors)
    setError(null)
    if (Object.keys(errors).length > 0) return

    setSaving(true)
    try {
      const payee = await createPayee(form.nickname.trim(), form.accountNumber.trim())
      showToast({ title: 'Payee saved', message: `${payee.nickname} (${payee.maskedAccountNumber})`, kind: 'success' })
      setForm({ nickname: '', accountNumber: '' })
      payees.reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(payee) {
    if (!window.confirm(`Remove ${payee.nickname} from your payees?`)) return
    try {
      await deletePayee(payee.id)
      showToast({ title: 'Payee removed', message: payee.nickname })
      payees.reload()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <>
      <h1>Payees</h1>
      <p className="muted">Save the people you pay often, then pick them by name when you make a transfer.</p>

      <form className="card form-card" onSubmit={handleAdd} noValidate aria-labelledby="add-payee-heading">
        <h2 id="add-payee-heading">Add a payee</h2>
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="nickname">Name</label>
          <input
            id="nickname"
            maxLength={60}
            value={form.nickname}
            onChange={(e) => setForm((f) => ({ ...f, nickname: e.target.value }))}
            aria-invalid={Boolean(fieldErrors.nickname)}
          />
          {fieldErrors.nickname && <p className="field__error">{fieldErrors.nickname}</p>}
        </div>
        <div className="field">
          <label htmlFor="payeeAccountNumber">Account number</label>
          <input
            id="payeeAccountNumber"
            inputMode="numeric"
            autoComplete="off"
            value={form.accountNumber}
            onChange={(e) => setForm((f) => ({ ...f, accountNumber: e.target.value }))}
            aria-invalid={Boolean(fieldErrors.accountNumber)}
          />
          {fieldErrors.accountNumber ? (
            <p className="field__error">{fieldErrors.accountNumber}</p>
          ) : (
            <p className="muted small field__hint">Demo: Ravi&apos;s savings account is 100000000003.</p>
          )}
        </div>
        <button type="submit" className="button button--primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save payee'}
        </button>
      </form>

      <section className="section" aria-labelledby="payee-list-heading">
        <h2 id="payee-list-heading">Saved payees</h2>
        {payees.status === 'loading' && <Spinner label="Loading payees…" />}
        {payees.status === 'error' && (
          <Alert kind="error" onRetry={payees.reload}>
            {payees.error.message}
          </Alert>
        )}
        {payees.status === 'success' && payees.data.length === 0 && (
          <div className="card empty">
            <p className="muted">No payees yet. Add one above.</p>
          </div>
        )}
        {payees.status === 'success' && payees.data.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <caption className="visually-hidden">Saved payees</caption>
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Account</th>
                  <th scope="col">Added</th>
                  <th scope="col">
                    <span className="visually-hidden">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {payees.data.map((payee) => (
                  <tr key={payee.id}>
                    <td>
                      <strong>{payee.nickname}</strong>
                    </td>
                    <td>{payee.maskedAccountNumber}</td>
                    <td>{formatDateTime(payee.createdAt)}</td>
                    <td className="num">
                      <div className="button-row" style={{ justifyContent: 'flex-end' }}>
                        <Link to={`/transfer?payee=${payee.id}`} className="button button--small">
                          Pay
                        </Link>
                        <button type="button" className="button button--small button--ghost" onClick={() => handleDelete(payee)}>
                          Remove
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}
