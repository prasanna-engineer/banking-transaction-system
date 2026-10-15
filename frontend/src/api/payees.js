import { apiRequest } from './client'

export function listPayees(signal) {
  return apiRequest('/payees', { signal })
}

export function createPayee(nickname, accountNumber) {
  return apiRequest('/payees', { method: 'POST', body: { nickname, accountNumber } })
}

export function deletePayee(id) {
  return apiRequest(`/payees/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
