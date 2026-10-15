import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, clearSession, refreshTokenStore, setUnauthorizedHandler, storeSession, tokenStore } from './client'

function json(status, body) {
  return Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status }))
}

describe('apiRequest token renewal', () => {
  let onUnauthorized

  beforeEach(() => {
    storeSession({ accessToken: 'old-access', refreshToken: 'refresh-1' })
    onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
  })

  it('renews an expired access token once and retries the request', async () => {
    const fetchMock = vi.fn((url, init) => {
      if (url.endsWith('/auth/refresh')) return json(200, { accessToken: 'new-access', refreshToken: 'refresh-2' })
      return init.headers.Authorization === 'Bearer new-access' ? json(200, { ok: true }) : json(401, { code: 'INVALID_TOKEN' })
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/accounts')).resolves.toEqual({ ok: true })
    expect(tokenStore.get()).toBe('new-access')
    expect(refreshTokenStore.get()).toBe('refresh-2')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('shares one refresh between requests that expire at the same time', async () => {
    const fetchMock = vi.fn((url, init) => {
      if (url.endsWith('/auth/refresh')) return json(200, { accessToken: 'new-access', refreshToken: 'refresh-2' })
      return init.headers.Authorization === 'Bearer new-access' ? json(200, { ok: true }) : json(401, {})
    })
    vi.stubGlobal('fetch', fetchMock)

    await Promise.all([apiRequest('/accounts'), apiRequest('/transactions'), apiRequest('/users/me')])
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1)
  })

  it('signs the user out when the session cannot be renewed', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => (url.endsWith('/auth/refresh') ? json(401, {}) : json(401, { code: 'INVALID_TOKEN', message: 'Expired' }))))

    await expect(apiRequest('/accounts')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('never tries to renew for unauthenticated calls such as a failed login', async () => {
    const fetchMock = vi.fn(() => json(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
