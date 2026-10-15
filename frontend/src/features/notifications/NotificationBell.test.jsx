import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listNotifications, markAllNotificationsRead } from '../../api/notifications'
import NotificationBell from './NotificationBell'
import { useLive } from './LiveContext'

vi.mock('../../api/notifications', () => ({
  listNotifications: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
}))

// Replace the live connection with plain state the test controls.
vi.mock('./LiveContext', () => ({ useLive: vi.fn() }))

function Harness({ initialUnread }) {
  const [unreadCount, setUnreadCount] = useState(initialUnread)
  useLive.mockReturnValue({ unreadCount, setUnreadCount, latest: null, connected: true })
  return <NotificationBell />
}

const NOTIFICATIONS = [
  { id: 2, type: 'MONEY_RECEIVED', title: 'Money received', message: '₹500.00 received in ••••0003 from ••••0001.', read: false, createdAt: '2026-10-15T10:00:00Z' },
  { id: 1, type: 'MONEY_SENT', title: 'Money sent', message: '₹20.00 sent.', read: true, createdAt: '2026-10-14T10:00:00Z' },
]

describe('NotificationBell', () => {
  beforeEach(() => {
    listNotifications.mockResolvedValue({ content: NOTIFICATIONS })
    markAllNotificationsRead.mockResolvedValue({ updated: 1 })
  })

  it('announces the unread count to screen readers', () => {
    render(<Harness initialUnread={3} />)
    expect(screen.getByRole('button', { name: 'Notifications, 3 unread' })).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('opens a panel with the latest notifications', async () => {
    render(<Harness initialUnread={1} />)
    fireEvent.click(screen.getByRole('button', { name: /Notifications/ }))

    expect(await screen.findByText('₹500.00 received in ••••0003 from ••••0001.')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Notifications' })).toBeInTheDocument()
  })

  it('marks everything as read', async () => {
    render(<Harness initialUnread={1} />)
    fireEvent.click(screen.getByRole('button', { name: /Notifications/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Mark all as read' }))

    await waitFor(() => expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument())
    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1)
  })

  it('closes with the Escape key', async () => {
    render(<Harness initialUnread={0} />)
    fireEvent.click(screen.getByRole('button', { name: 'Notifications' }))
    await screen.findByRole('dialog')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
