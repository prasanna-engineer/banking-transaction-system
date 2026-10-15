// The one place that talks HTTP. Every API call goes through apiRequest(), so the bearer token,
// JSON handling and error format are handled once instead of on every page.

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1'

// Token storage choice: sessionStorage. It survives a page refresh but is cleared when the tab closes,
// and it is not sent automatically with requests (so no CSRF). Trade-off: like localStorage, any
// injected script (XSS) could read it - so we never render untrusted HTML. See README / milestone notes.
const TOKEN_KEY = 'banking.accessToken'

export const tokenStore = {
  get() {
    try {
      return sessionStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token) {
    try {
      sessionStorage.setItem(TOKEN_KEY, token)
    } catch {
      // Storage blocked (private mode etc.): the session simply won't survive a refresh.
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      // ignore
    }
  },
}

// AuthContext registers a callback here so an expired token logs the user out everywhere.
let unauthorizedHandler = () => {}
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/** Mirrors the backend's ErrorResponse JSON: { status, code, message, fieldErrors }. */
export class ApiError extends Error {
  constructor(status, code, message, fieldErrors = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

/**
 * Downloads a file (e.g. a CSV statement) with the bearer token and saves it in the browser.
 * A plain <a href> link cannot send the Authorization header, so the file is fetched, then saved.
 */
export async function apiDownload(path, fallbackName) {
  const token = tokenStore.get()
  let response
  try {
    response = await fetch(API_BASE_URL + path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running on port 8081?')
  }
  if (!response.ok) {
    const data = await response.json().catch(() => null)
    if (response.status === 401) unauthorizedHandler()
    throw new ApiError(response.status, data?.code ?? `HTTP_${response.status}`, data?.message ?? 'Download failed.')
  }
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function apiRequest(path, { method = 'GET', body, auth = true, signal, headers: extraHeaders } = {}) {
  const headers = { Accept: 'application/json', ...extraHeaders }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = tokenStore.get()
  if (auth && token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response
  try {
    response = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running on port 8081?')
  }

  if (response.status === 204) {
    return null
  }
  const data = await response.json().catch(() => null)

  if (!response.ok) {
    // 401 on a protected call means our token is missing/expired: log out. (A failed login is
    // also a 401, but that call uses auth: false and must not trigger this.)
    if (response.status === 401 && auth) {
      unauthorizedHandler()
    }
    throw new ApiError(
      response.status,
      data?.code ?? `HTTP_${response.status}`,
      data?.message ?? `Request failed (${response.status}).`,
      data?.fieldErrors ?? [],
    )
  }
  return data
}
