import { Link, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import CreateAccount from './pages/CreateAccount'
import AccountDetail from './pages/AccountDetail'
import Transfer from './pages/Transfer'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/accounts/new" element={<CreateAccount />} />
        <Route path="/accounts/:accountNumber" element={<AccountDetail />} />
        <Route path="/transfer" element={<Transfer />} />
        <Route
          path="*"
          element={
            <div className="card">
              <h1>Page not found</h1>
              <Link to="/">Back to dashboard</Link>
            </div>
          }
        />
      </Route>
    </Routes>
  )
}
