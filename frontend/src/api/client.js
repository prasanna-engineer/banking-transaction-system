// Thin wrapper around fetch() for the Spring Boot API.
// Errors come back as RFC 9457 problem details: { title, status, detail, errors? }.

export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request(path, { method = 'GET', body } = {}) {
  let response
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running on port 8081?')
  }

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, data?.detail || `Request failed (${response.status})`, data?.errors || {})
  }
  return data
}

export const api = {
  health: () => request('/health'),
  listAccounts: () => request('/accounts'),
  getAccount: (number) => request(`/accounts/${number}`),
  createAccount: (payload) => request('/accounts', { method: 'POST', body: payload }),
  closeAccount: (number) => request(`/accounts/${number}/close`, { method: 'PATCH' }),
  deposit: (number, payload) => request(`/accounts/${number}/deposit`, { method: 'POST', body: payload }),
  withdraw: (number, payload) => request(`/accounts/${number}/withdraw`, { method: 'POST', body: payload }),
  transactions: (number, page = 0, size = 10) => request(`/accounts/${number}/transactions?page=${page}&size=${size}`),
  transfer: (payload) => request('/transfers', { method: 'POST', body: payload }),
}
