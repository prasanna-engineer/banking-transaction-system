import { useEffect, useRef, useState } from 'react'
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/notifications'
import { formatDateTime } from '../../utils/format'
import { useLive } from './LiveContext'

/** Bell icon with an unread badge; opens a panel with the latest notifications. */
export default function NotificationBell() {
  const { unreadCount, setUnreadCount, latest, connected } = useLive()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)
  const panelRef = useRef(null)

  // Load the list each time the panel opens, and whenever a new notification arrives while it is open.
  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    listNotifications({ size: 8 }, controller.signal)
      .then((page) => setItems(page.content))
      .catch((err) => err.name !== 'AbortError' && setError(err.message))
    return () => controller.abort()
  }, [open, latest])

  // Close on Escape or a click outside the panel.
  useEffect(() => {
    if (!open) return
    function onKey(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    function onClick(event) {
      if (panelRef.current && !panelRef.current.contains(event.target)) setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onClick)
    }
  }, [open])

  async function handleMarkAll() {
    await markAllNotificationsRead()
    setUnreadCount(0)
    setItems((list) => list?.map((n) => ({ ...n, read: true })))
  }

  async function handleMarkOne(notification) {
    if (notification.read) return
    await markNotificationRead(notification.id)
    setUnreadCount((count) => Math.max(0, count - 1))
    setItems((list) => list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)))
  }

  const label = unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'

  return (
    <div className="bell" ref={panelRef}>
      <button
        type="button"
        className="bell__button"
        aria-label={label}
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpen((value) => !value)}
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" aria-hidden="true">
          <path d="M6 16V11a6 6 0 1112 0v5l2 2H4l2-2zM10 20a2 2 0 004 0" strokeLinejoin="round" />
        </svg>
        {unreadCount > 0 && <span className="bell__count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
        <span className={`bell__live ${connected ? 'bell__live--on' : ''}`} title={connected ? 'Live' : 'Reconnecting…'} />
      </button>

      {open && (
        <div className="bell__panel" role="dialog" aria-label="Notifications">
          <div className="bell__header">
            <strong>Notifications</strong>
            {unreadCount > 0 && (
              <button type="button" className="button button--small button--ghost" onClick={handleMarkAll}>
                Mark all as read
              </button>
            )}
          </div>
          {error && <p className="bell__empty">{error}</p>}
          {!error && items === null && <p className="bell__empty">Loading…</p>}
          {items?.length === 0 && <p className="bell__empty">No notifications yet.</p>}
          {items?.length > 0 && (
            <ul className="bell__list">
              {items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`bell__item ${n.read ? '' : 'bell__item--unread'}`}
                    onClick={() => handleMarkOne(n)}
                  >
                    <span className="bell__title">{n.title}</span>
                    <span className="bell__message">{n.message}</span>
                    <span className="bell__time">{formatDateTime(n.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
