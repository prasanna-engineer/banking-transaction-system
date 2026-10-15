import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../../api/auth'
import { setUnauthorizedHandler, tokenStore } from '../../api/client'

const AuthContext = createContext(null)

/**
 * Holds "who is logged in" for the whole app (like Laravel's auth() helper, but in the browser).
 * status: 'loading' while we check a saved token, then 'authenticated' or 'anonymous'.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState(tokenStore.get() ? 'loading' : 'anonymous')
  const [sessionExpired, setSessionExpired] = useState(false)

  const clearSession = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    setStatus('anonymous')
  }, [])

  // Any API call that comes back 401 (e.g. token expired after 30 minutes) ends the session.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setSessionExpired(true)
      clearSession()
    })
  }, [clearSession])

  // On page load with a saved token, ask the server who we are (the token may have expired).
  useEffect(() => {
    if (!tokenStore.get()) {
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
    tokenStore.set(result.accessToken)
    setSessionExpired(false)
    setUser(result.user)
    setStatus('authenticated')
    return result.user
  }, [])

  const logout = useCallback(() => {
    setSessionExpired(false)
    clearSession()
  }, [clearSession])

  const value = useMemo(
    () => ({ user, status, sessionExpired, login, logout }),
    [user, status, sessionExpired, login, logout],
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
