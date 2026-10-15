import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../../api/auth'
import { clearSession as clearStoredTokens, refreshTokenStore, setUnauthorizedHandler, storeSession, tokenStore } from '../../api/client'

const AuthContext = createContext(null)

const hasStoredSession = () => Boolean(tokenStore.get() || refreshTokenStore.get())

/**
 * Holds "who is logged in" for the whole app (like Laravel's auth() helper, but in the browser).
 * status: 'loading' while we check a saved session, then 'authenticated' or 'anonymous'.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState(hasStoredSession() ? 'loading' : 'anonymous')
  const [sessionExpired, setSessionExpired] = useState(false)

  const clearSession = useCallback(() => {
    clearStoredTokens()
    setUser(null)
    setStatus('anonymous')
  }, [])

  // A request that is still 401 after trying to renew the session (refresh token expired, revoked
  // or reused) ends the session in the browser too.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setSessionExpired(true)
      clearSession()
    })
  }, [clearSession])

  // On page load with a saved session, ask the server who we are. The API client renews an expired
  // access token automatically, so a refresh after 15+ minutes still works.
  useEffect(() => {
    if (!hasStoredSession()) {
      return
    }
    const controller = new AbortController()
    authApi
      .getMe(controller.signal)
      .then((me) => {
        setUser(me)
        setStatus('authenticated')
      })
      .catch((error) => {
        if (error.name !== 'AbortError') {
          clearSession()
        }
      })
    return () => controller.abort()
  }, [clearSession])

  const login = useCallback(async (email, password) => {
    const result = await authApi.login(email, password)
    storeSession(result)
    setSessionExpired(false)
    setUser(result.user)
    setStatus('authenticated')
    return result.user
  }, [])

  /** Signs out here and tells the server, so this session's refresh token cannot be used again. */
  const logout = useCallback(() => {
    const refreshToken = refreshTokenStore.get()
    if (refreshToken) {
      authApi.logout(refreshToken).catch(() => {}) // best effort: the local sign-out happens regardless
    }
    setSessionExpired(false)
    clearSession()
  }, [clearSession])

  /** Signs out every session on every device (server revokes all tokens and closes live connections). */
  const logoutEverywhere = useCallback(async () => {
    await authApi.logoutEverywhere()
    setSessionExpired(false)
    clearSession()
  }, [clearSession])

  const value = useMemo(
    () => ({ user, status, sessionExpired, login, logout, logoutEverywhere }),
    [user, status, sessionExpired, login, logout, logoutEverywhere],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
