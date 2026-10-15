import { API_BASE_URL, apiRequest } from './client'

export function listNotifications({ page = 0, size = 10 } = {}, signal) {
  return apiRequest(`/notifications?page=${page}&size=${size}`, { signal })
}

export function getUnreadCount(signal) {
  return apiRequest('/notifications/unread-count', { signal })
}

export function markNotificationRead(id) {
  return apiRequest(`/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' })
}

export function markAllNotificationsRead() {
  return apiRequest('/notifications/read-all', { method: 'POST' })
}

/**
 * Opens the live event stream. EventSource cannot send our Authorization header, so we first exchange
 * the token for a one-time ticket (valid 60 s) and put that in the URL instead.
 */
export async function openLiveStream() {
  const { ticket } = await apiRequest('/notifications/stream-ticket', { method: 'POST' })
  return new EventSource(`${API_BASE_URL}/notifications/stream?ticket=${encodeURIComponent(ticket)}`)
}
