import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listAccounts } from '../../api/accounts'
import { createTransfer, newIdempotencyKey } from '../../api/transfers'
import { useApi } from '../../api/useApi'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { maskAccountNumber, titleCase } from '../../utils/format'
import { formatMoney } from '../../utils/money'

// Up to 15 digits, optionally a dot and 1-2 decimals. Matches the server's @Digits(15, 2) rule.
const AMOUNT_PATTERN = /^\d{1,15}(\.\d{1,2})?$/
const ACCOUNT_NUMBER_PATTERN = /^\d{6,20}$/

const EMPTY_FORM = {
  sourceAccountId: '',
  destinationType: 'own',
  destinationAccountId: '',
  destinationAccountNumber: '',
  amount: '',
  description: '',
}

/**
 * Three steps: fill in the form -> review -> result.
 * The review step exists so a typo in the amount or account is caught before money moves.
 */
export default function TransferPage() {
  const accounts = useApi((signal) => listAccounts(signal), [])
  const [step, setStep] = useState('form')
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitError, setSubmitError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  // One key per reviewed transfer: pressing Confirm again after an error re-sends the same key,
  // so the server can never process this transfer twice.
  const [idempotencyKey, setIdempotencyKey] = useState(null)

  // Only block the page on the first load; a refresh after a transfer keeps showing the old data.
  if (!accounts.data && accounts.status === 'loading') return <Spinner label="Loading your accounts…" />
  if (!accounts.data && accounts.status === 'error') {
    return (
      <Alert kind="error" onRetry={accounts.reload}>
        {accounts.error.message}
      </Alert>
    )
  }

  const activeAccounts = accounts.data.filter((a) => a.status === 'ACTIVE')
  const byId = new Map(accounts.data.map((a) => [String(a.id), a]))
  const source = byId.get(form.sourceAccountId)
  const ownDestination = byId.get(form.destinationAccountId)

  function update(field) {
    return (event) => setForm((previous) => ({ ...previous, [field]: event.target.value }))
  }

  function validate() {
    const errors = {}
    if (!source) errors.sourceAccountId = 'Choose the account to send from.'
    if (form.destinationType === 'own') {
      if (!ownDestination) errors.destinationAccountId = 'Choose the account to send to.'
      else if (form.destinationAccountId === form.sourceAccountId)
        errors.destinationAccountId = 'Choose a different account from the source.'
    } else if (!ACCOUNT_NUMBER_PATTERN.test(form.destinationAccountNumber.trim())) {
      errors.destinationAccountNumber = 'Enter the 6–20 digit account number.'
    } else if (form.destinationAccountNumber.trim() === source?.accountNumber) {
      errors.destinationAccountNumber = 'This is the same as the source account.'
    }
    const amount = form.amount.trim()
    if (!AMOUNT_PATTERN.test(amount)) errors.amount = 'Enter an amount like 250 or 250.75 (at most 2 decimals).'
    else if (/^0+(\.0+)?$/.test(amount)) errors.amount = 'The amount must be greater than zero.'
    if (form.description.length > 255) errors.description = 'Keep the description under 255 characters.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  function handleReview(event) {
    event.preventDefault()
    setSubmitError(null)
    if (validate()) {
      setIdempotencyKey(newIdempotencyKey())
      setStep('review')
    }
  }

  async function handleConfirm() {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const body = {
        sourceAccountId: Number(form.sourceAccountId),
        amount: form.amount.trim(), // sent as a string: never converted to a float
        description: form.description.trim() || undefined,
        ...(form.destinationType === 'own'
          ? { destinationAccountId: Number(form.destinationAccountId) }
          : { destinationAccountNumber: form.destinationAccountNumber.trim() }),
      }
      setResult(await createTransfer(body, idempotencyKey))
      setStep('done')
      accounts.reload() // balances come from the server, never calculated here
    } catch (err) {
      setSubmitError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  function startOver() {
    setForm(EMPTY_FORM)
    setResult(null)
    setStep('form')
  }

  const destinationLabel =
    form.destinationType === 'own'
      ? ownDestination && `${titleCase(ownDestination.accountType)} ${maskAccountNumber(ownDestination.accountNumber)}`
      : `Account ${maskAccountNumber(form.destinationAccountNumber.trim())}`

  return (
    <>
      <h1>Transfer money</h1>
      <ol className="steps" aria-label="Transfer steps">
        <li aria-current={step === 'form' ? 'step' : undefined}>Details</li>
        <li aria-current={step === 'review' ? 'step' : undefined}>Review</li>
        <li aria-current={step === 'done' ? 'step' : undefined}>Done</li>
      </ol>

      {step === 'form' && activeAccounts.length === 0 && (
        <div className="card empty">
          <p className="muted">You need an active account to make a transfer.</p>
        </div>
      )}

      {step === 'form' && activeAccounts.length > 0 && (
        <form className="card form-card" onSubmit={handleReview} noValidate>
          <Field label="From" name="sourceAccountId" error={fieldErrors.sourceAccountId}>
            <select id="sourceAccountId" value={form.sourceAccountId} onChange={update('sourceAccountId')}>
              <option value="">Select an account…</option>
              {activeAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {titleCase(a.accountType)} {maskAccountNumber(a.accountNumber)} — {formatMoney(a.balance, a.currency)}
                </option>
              ))}
            </select>
          </Field>

          <fieldset className="field">
            <legend>To</legend>
            <label className="radio">
              <input
                type="radio"
                name="destinationType"
                value="own"
                checked={form.destinationType === 'own'}
                onChange={update('destinationType')}
              />
              One of my accounts
            </label>
            <label className="radio">
              <input
                type="radio"
                name="destinationType"
                value="other"
                checked={form.destinationType === 'other'}
                onChange={update('destinationType')}
              />
              Someone else's account number
            </label>
          </fieldset>

          {form.destinationType === 'own' ? (
            <Field label="Destination account" name="destinationAccountId" error={fieldErrors.destinationAccountId}>
              <select
                id="destinationAccountId"
                value={form.destinationAccountId}
                onChange={update('destinationAccountId')}
              >
                <option value="">Select an account…</option>
                {activeAccounts
                  .filter((a) => String(a.id) !== form.sourceAccountId)
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {titleCase(a.accountType)} {maskAccountNumber(a.accountNumber)}
                    </option>
                  ))}
              </select>
            </Field>
          ) : (
            <Field
              label="Destination account number"
              name="destinationAccountNumber"
              error={fieldErrors.destinationAccountNumber}
              hint="Demo: Ravi's savings account is 100000000003."
            >
              <input
                id="destinationAccountNumber"
                inputMode="numeric"
                autoComplete="off"
                value={form.destinationAccountNumber}
                onChange={update('destinationAccountNumber')}
              />
            </Field>
          )}

          <Field label="Amount (INR)" name="amount" error={fieldErrors.amount}>
            <input id="amount" inputMode="decimal" autoComplete="off" value={form.amount} onChange={update('amount')} />
          </Field>

          <Field label="Description (optional)" name="description" error={fieldErrors.description}>
            <input id="description" maxLength={255} value={form.description} onChange={update('description')} />
          </Field>

          <button type="submit" className="button button--primary">
            Review transfer
          </button>
        </form>
      )}

      {step === 'review' && (
        <div className="card form-card">
          <h2>Please check the details</h2>
          {submitError && <Alert kind="error">{submitError}</Alert>}
          <dl className="details">
            <dt>From</dt>
            <dd>
              {titleCase(source.accountType)} {maskAccountNumber(source.accountNumber)}
            </dd>
            <dt>To</dt>
            <dd>{destinationLabel}</dd>
            <dt>Amount</dt>
            <dd className="balance">{formatMoney(form.amount.trim(), source.currency)}</dd>
            <dt>Description</dt>
            <dd>{form.description.trim() || '—'}</dd>
          </dl>
          <div className="button-row">
            <button type="button" className="button" onClick={() => setStep('form')} disabled={submitting}>
              Edit
            </button>
            <button type="button" className="button button--primary" onClick={handleConfirm} disabled={submitting}>
              {submitting ? 'Sending…' : 'Confirm transfer'}
            </button>
          </div>
        </div>
      )}

      {step === 'done' && result && (
        <div className="card form-card">
          <Alert kind="success">Transfer {titleCase(result.status)}.</Alert>
          <dl className="details">
            <dt>Reference</dt>
            <dd>
              <code>{result.reference}</code>
            </dd>
            <dt>Amount</dt>
            <dd className="balance">{formatMoney(result.amount, result.currency)}</dd>
            <dt>New balance</dt>
            <dd>
              {accounts.status === 'success' && accounts.data
                ? formatMoney(
                    accounts.data.find((a) => a.id === result.sourceAccountId)?.balance ?? '0',
                    result.currency,
                  )
                : 'Refreshing…'}
            </dd>
          </dl>
          <div className="button-row">
            <button type="button" className="button button--primary" onClick={startOver}>
              Make another transfer
            </button>
            <Link to="/transactions" className="button">
              View transactions
            </Link>
          </div>
        </div>
      )}
    </>
  )
}

function Field({ label, name, error, hint, children }) {
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      {children}
      {hint && !error && <p className="muted small field__hint">{hint}</p>}
      {error && (
        <p id={`${name}-error`} className="field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
