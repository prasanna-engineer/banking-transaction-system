import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { api } from '../api/client'

export default function Layout() {
  const [online, setOnline] = useState(null)

  useEffect(() => {
    api.health().then(() => setOnline(true)).catch(() => setOnline(false))
  }, [])

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <span className="brand">Banking System</span>
          <nav className="nav">
            <NavLink to="/" end>Dashboard</NavLink>
            <NavLink to="/accounts/new">Open Account</NavLink>
            <NavLink to="/transfer">Transfer</NavLink>
          </nav>
          <span className={`status-dot ${online === null ? '' : online ? 'up' : 'down'}`}>
            {online === null ? 'Checking…' : online ? 'API online' : 'API offline'}
          </span>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  )
}
