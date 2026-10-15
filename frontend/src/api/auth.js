import { apiRequest } from './client'

export function login(email, password) {
  return apiRequest('/auth/login', { method: 'POST', body: { email, password }, auth: false })
}

export function register(fullName, email, password) {
  return apiRequest('/auth/register', { method: 'POST', body: { fullName, email, password }, auth: false })
}

export function getMe(signal) {
  return apiRequest('/users/me', { signal })
}

/** Ends this session on the server (its refresh token stops working). */
export function logout(refreshToken) {
  return apiRequest('/auth/logout', { method: 'POST', body: { refreshToken }, auth: false })
}

/** Ends every session of this user, on every device. */
export function logoutEverywhere() {
  return apiRequest('/auth/logout-all', { method: 'POST' })
}
