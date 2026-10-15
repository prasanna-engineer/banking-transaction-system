import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { getUnreadCount, openLiveStream } from '../../api/notifications'
import { useToast } from '../../components/Toaster'
import { useAuth } from '../auth/AuthContext'

const LiveContext = createContext(null)

const MONEY_TYPES = new Set(['MONEY_RECEIVED', 'MONEY_SENT', 'INTERNAL_TRANSFER'])
const MAX_RETRY_DELAY_MS = 30_000

/**
 * One live connection to the server for the whole signed-in app (Server-Sent Events).
 *
 * - "notification": updates the bell, shows a toast, and bumps moneyVersion so pages showing balances
 *   reload themselves. The data is always re-read from the API - events are only a "something changed" hint.
 * - "audit" (admins): bumps auditVersion so the audit log refreshes.
 * - "session-ended": the account was disabled; sign out at once.
 *
 * If the connection drops, it reconnects with a NEW ticket (tickets are single-use), waiting a little
 * longer after each failure (1 s, 2 s, 4 s ... up to 30 s) so a server outage is not hammered.
 */
export function LiveProvider({ children }) {
  const { logout } = useAuth()
  const { showToast } = useToast()
  const [unreadCount, setUnreadCount] = useState(0)
  const [latest, setLatest] = useState(null)
  const [moneyVersion, setMoneyVersion] = useState(0)
  const [auditVersion, setAuditVersion] = useState(0)
  const [connected, setConnected] = useState(false)

  const refreshUnreadCount = useCallback(() => {
    getUnreadCount()
      .then((result) => setUnreadCount(result.count))
      .catch(() => {})
  }, [])

  useEffect(() => {
    let source = null
    let retryTimer = null
    let attempt = 0
    let stopped = false

    function scheduleReconnect() {
      if (stopped) return
      const delay = Math.min(1000 * 2 ** attempt, MAX_RETRY_DELAY_MS)
      attempt += 1
      retryTimer = setTimeout(connect, delay)
    }

    async function connect() {
      try {
        source = await openLiveStream()
      } catch {
        scheduleReconnect()
        return
      }
      if (stopped) {
        source.close()
        return
      }
      source.addEventListener('connected', () => {
        attempt = 0
        setConnected(true)
        refreshUnreadCount() // catch up on anything that arrived while we were offline
      })
      source.addEventListener('notification', (event) => {
        const notification = JSON.parse(event.data)
        setLatest(notification)
        setUnreadCount((count) => count + 1)
        if (MONEY_TYPES.has(notification.type)) setMoneyVersion((v) => v + 1)
        showToast({
          title: notification.title,
          message: notification.message,
          kind: notification.type === 'MONEY_RECEIVED' ? 'success' : 'info',
        })
      })
      source.addEventListener('audit', () => setAuditVersion((v) => v + 1))
      source.addEventListener('session-ended', () => {
        stopped = true
        source.close()
        showToast({ title: 'Signed out', message: 'Your account was disabled by an administrator.', kind: 'error' })
        logout()
      })
      source.onerror = () => {
        // EventSource would retry with the same (already used) ticket; do it ourselves with a new one.
        setConnected(false)
        source.close()
        scheduleReconnect()
      }
    }

    connect()
    return () => {
      stopped = true
      clearTimeout(retryTimer)
      source?.close()
    }
  }, [logout, refreshUnreadCount, showToast])

  const value = useMemo(
    () => ({ unreadCount, setUnreadCount, latest, moneyVersion, auditVersion, connected, refreshUnreadCount }),
    [unreadCount, latest, moneyVersion, auditVersion, connected, refreshUnreadCount],
  )
  return <LiveContext.Provider value={value}>{children}</LiveContext.Provider>
}

/** Live state, or harmless defaults when rendered outside the signed-in app (e.g. in tests). */
export function useLive() {
  return (
    useContext(LiveContext) ?? {
      unreadCount: 0,
      setUnreadCount: () => {},
      latest: null,
      moneyVersion: 0,
      auditVersion: 0,
      connected: false,
      refreshUnreadCount: () => {},
    }
  )
}
