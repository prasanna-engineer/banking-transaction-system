import { apiDownload, apiRequest } from './client'

function toQuery(params) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value)
    }
  })
  return query.toString() ? `?${query}` : ''
}

/** params: { accountId, from, to, type, status, page, size, sort } - empty values are left out. */
export function searchTransactions(params = {}, signal) {
  return apiRequest(`/transactions${toQuery(params)}`, { signal })
}

/** Saves the transactions matching these filters as a CSV statement. */
export function downloadStatement({ accountId, from, to, type, status } = {}) {
  return apiDownload(`/transactions/export${toQuery({ accountId, from, to, type, status })}`, 'transactions.csv')
}
