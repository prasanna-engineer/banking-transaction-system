import { apiRequest } from './client'

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  return search.toString() ? `?${search}` : ''
}

export function listUsers({ q, page = 0, size = 20 } = {}, signal) {
  return apiRequest(`/admin/users${query({ q, page, size })}`, { signal })
}

export function changeUserStatus(userId, status) {
  return apiRequest(`/admin/users/${encodeURIComponent(userId)}/status`, { method: 'PATCH', body: { status } })
}

export function listAuditLogs({ action, entityType, page = 0, size = 20 } = {}, signal) {
  return apiRequest(`/admin/audit-logs${query({ action, entityType, page, size })}`, { signal })
}
