// The one place that talks HTTP. Every API call goes through apiRequest(), so the bearer token,
// JSON handling, error format and token renewal are handled once instead of on every page.

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api/v1'

// Token storage choice: sessionStorage. It survives a page refresh but is cleared when the tab closes,
// and it is not sent automatically with requests (so no CSRF). Trade-off: like localStorage, any
// injected script (XSS) could read it - so we never render untrusted HTML. The access token lives
// 15 minutes; the refresh token is single-use and revocable on the server (see README).
function storage(key) {
  return {
    get() {
      try {
        return sessionStorage.getItem(key)
      } catch {
        return null
      }
    },
    set(value) {
      try {
        sessionStorage.setItem(key, value)
      } catch {
        // Storage blocked (private mode etc.): the session simply won't survive a page refresh.
      }
    },
    clear() {
      try {
        sessionStorage.removeItem(key)
      } catch {
        // ignore
      }
    },
  }
}

export const tokenStore = storage('banking.accessToken')
export const refreshTokenStore = storage('banking.refreshToken')

/** Keep both tokens from a login or refresh response. */
export function storeSession({ accessToken, refreshToken }) {
  tokenStore.set(accessToken)
  refreshTokenStore.set(refreshToken)
}

export function clearSession() {
  tokenStore.clear()
  refreshTokenStore.clear()
}

// AuthContext registers a callback here so a session that cannot be renewed logs the user out everywhere.
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

const NETWORK_ERROR = () => new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running on port 8081?')

// If several requests find the access token expired at the same moment, they share ONE refresh call:
// a refresh token is single-use, so two parallel refreshes would look like token theft to the server.
let refreshInFlight = null

/** Swaps the refresh token for new tokens. Resolves true on success, false if the session is over. */
function renewSession() {
  const refreshToken = refreshTokenStore.get()
  if (!refreshToken) return Promise.resolve(false)
  if (!refreshInFlight) {
    refreshInFlight = fetch(API_BASE_URL + '/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) return false
        storeSession(await response.json())
        return true
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

/**
 * fetch() with the bearer token. On a 401 it renews the session once and retries; if that is not
 * possible the user is signed out (auth: false requests, like login itself, never do this).
 */
async function authorizedFetch(path, init, auth) {
  const send = () => {
    const token = tokenStore.get()
    const headers = { ...init.headers, ...(auth && token ? { Authorization: `Bearer ${token}` } : {}) }
    return fetch(API_BASE_URL + path, { ...init, headers })
  }
  let response
  try {
    response = await send()
    if (response.status === 401 && auth) {
      if (await renewSession()) {
        response = await send()
      }
      if (response.status === 401) {
        unauthorizedHandler()
      }
    }
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw NETWORK_ERROR()
  }
  return response
}

async function toApiError(response, fallbackMessage) {
  const data = await response.json().catch(() => null)
  return new ApiError(
    response.status,
    data?.code ?? `HTTP_${response.status}`,
    data?.message ?? fallbackMessage ?? `Request failed (${response.status}).`,
    data?.fieldErrors ?? [],
  )
}

/**
 * Downloads a file (e.g. a CSV statement) with the bearer token and saves it in the browser.
 * A plain <a href> link cannot send the Authorization header, so the file is fetched, then saved.
 */
export async function apiDownload(path, fallbackName) {
  const response = await authorizedFetch(path, {}, true)
  if (!response.ok) throw await toApiError(response, 'Download failed.')
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
  const response = await authorizedFetch(
    path,
    { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal },
    auth,
  )
  if (response.status === 204) {
    return null
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.json().catch(() => null)
}
