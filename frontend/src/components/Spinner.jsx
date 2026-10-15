export default function Spinner({ label = 'Loading…', fullPage = false }) {
  return (
    <div className={fullPage ? 'spinner spinner--page' : 'spinner'} role="status" aria-live="polite">
      <span className="spinner__circle" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
