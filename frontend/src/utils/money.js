// Money arrives from the API as strings like "49900.00". JavaScript numbers are binary floats
// (0.1 + 0.2 = 0.30000000000000004), so we never turn amounts into numbers. Display totals are
// added up as whole paise using BigInt, which is exact. Real balance changes happen only on the server.

function toPaise(amount) {
  const text = String(amount).trim()
  const negative = text.startsWith('-')
  const [whole, fraction = ''] = text.replace('-', '').split('.')
  const paise = BigInt(whole || '0') * 100n + BigInt((fraction + '00').slice(0, 2))
  return negative ? -paise : paise
}

function paiseToString(paise) {
  const negative = paise < 0n
  const abs = negative ? -paise : paise
  return `${negative ? '-' : ''}${abs / 100n}.${String(abs % 100n).padStart(2, '0')}`
}

/** Exact sum of decimal strings, returned as a decimal string. */
export function sumAmounts(amounts) {
  return paiseToString(amounts.reduce((total, amount) => total + toPaise(amount), 0n))
}

function currencySymbol(currency) {
  try {
    return (
      new Intl.NumberFormat('en-IN', { style: 'currency', currency })
        .formatToParts(0)
        .find((part) => part.type === 'currency')?.value ?? `${currency} `
    )
  } catch {
    return `${currency} `
  }
}

/** "1250000.5" + "INR" -> "₹12,50,000.50" (Indian digit grouping), without float rounding. */
export function formatMoney(amount, currency = 'INR') {
  const normalized = paiseToString(toPaise(amount))
  const negative = normalized.startsWith('-')
  const [whole, fraction] = normalized.replace('-', '').split('.')
  const grouped = new Intl.NumberFormat('en-IN').format(BigInt(whole))
  return `${negative ? '−' : ''}${currencySymbol(currency)}${grouped}.${fraction}`
}
