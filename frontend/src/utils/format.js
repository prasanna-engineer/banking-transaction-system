const currency = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })
const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

export const formatMoney = (value) => currency.format(Number(value ?? 0))

export const formatDateTime = (iso) => (iso ? dateTime.format(new Date(iso)) : '')

export const formatLabel = (value) =>
  (value ?? '').toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')

// Amount strings typed by the user: positive, at most 2 decimals.
export const isValidAmount = (value) => /^\d+(\.\d{1,2})?$/.test(value) && Number(value) > 0
