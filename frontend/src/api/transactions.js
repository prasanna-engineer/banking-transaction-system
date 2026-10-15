import { apiRequest } from './client'

/** params: { accountId, from, to, type, status, page, size, sort } - empty values are left out. */
export function searchTransactions(params = {}, signal) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value)
    }
  })
  const suffix = query.toString() ? `?${query}` : ''
  return apiRequest(`/transactions${suffix}`, { signal })
}
