import { apiRequest } from './client'

export function listAccounts(signal) {
  return apiRequest('/accounts', { signal })
}

export function getAccount(id, signal) {
  return apiRequest(`/accounts/${encodeURIComponent(id)}`, { signal })
}
