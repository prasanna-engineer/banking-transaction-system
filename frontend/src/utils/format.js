/** The API sends UTC timestamps; the browser shows them in the user's local time zone. */
export function formatDateTime(iso) {
  if (!iso) {
    return ''
  }
  return new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
}

/** "100000000001" -> "•••• 0001" */
export function maskAccountNumber(accountNumber) {
  if (!accountNumber) {
    return ''
  }
  return `•••• ${accountNumber.slice(-4)}`
}

export function titleCase(value) {
  if (!value) {
    return ''
  }
  return value.charAt(0) + value.slice(1).toLowerCase()
}
