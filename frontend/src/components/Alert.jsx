/** kind: 'error' | 'info' | 'success'. Errors use role="alert" so screen readers announce them. */
export default function Alert({ kind = 'info', children, onRetry }) {
  return (
    <div className={`alert alert--${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {onRetry && (
        <button type="button" className="button button--small" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
