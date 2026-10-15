import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import { register } from '../../api/auth'
import { useAuth } from './AuthContext'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const EMPTY = { fullName: '', email: '', password: '', confirmPassword: '' }

export default function RegisterPage() {
  const { login, status } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(EMPTY)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  function update(field) {
    return (event) => setForm((previous) => ({ ...previous, [field]: event.target.value }))
  }

  function validate() {
    const errors = {}
    if (!form.fullName.trim()) errors.fullName = 'Enter your name.'
    if (!EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Enter a valid email address.'
    if (form.password.length < 8 || form.password.length > 72) errors.password = 'Use 8 to 72 characters.'
    if (form.confirmPassword !== form.password) errors.confirmPassword = 'Passwords do not match.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    if (!validate()) return
    setSubmitting(true)
    try {
      await register(form.fullName.trim(), form.email.trim(), form.password)
      await login(form.email.trim(), form.password)
      navigate('/', { replace: true })
    } catch (err) {
      // Show the server's per-field messages next to the matching inputs.
      const serverErrors = Object.fromEntries((err.fieldErrors ?? []).map((f) => [f.field, f.message]))
      setFieldErrors(serverErrors)
      setError(err.message)
      setSubmitting(false)
    }
  }

  const fields = [
    { name: 'fullName', label: 'Full name', type: 'text', autoComplete: 'name' },
    { name: 'email', label: 'Email', type: 'email', autoComplete: 'email' },
    { name: 'password', label: 'Password (8–72 characters)', type: 'password', autoComplete: 'new-password' },
    { name: 'confirmPassword', label: 'Confirm password', type: 'password', autoComplete: 'new-password' },
  ]

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit} noValidate>
        <h1>Create a demo account</h1>
        <p className="muted">Use made-up details only. This is not a real bank.</p>
        {error && <Alert kind="error">{error}</Alert>}

        {fields.map((f) => (
          <div className="field" key={f.name}>
            <label htmlFor={f.name}>{f.label}</label>
            <input
              id={f.name}
              type={f.type}
              autoComplete={f.autoComplete}
              value={form[f.name]}
              onChange={update(f.name)}
              aria-invalid={Boolean(fieldErrors[f.name])}
              aria-describedby={fieldErrors[f.name] ? `${f.name}-error` : undefined}
            />
            {fieldErrors[f.name] && (
              <p id={`${f.name}-error`} className="field__error">
                {fieldErrors[f.name]}
              </p>
            )}
          </div>
        ))}

        <button type="submit" className="button button--primary button--block" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
        <p className="muted small">
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </div>
  )
}
