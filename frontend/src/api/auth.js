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
