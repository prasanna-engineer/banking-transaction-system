import { Route, Routes } from 'react-router-dom'
import AccountDetailPage from './features/accounts/AccountDetailPage'
import AccountsPage from './features/accounts/AccountsPage'
import AdminAuditLogsPage from './features/admin/AdminAuditLogsPage'
import AdminUsersPage from './features/admin/AdminUsersPage'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import DashboardPage from './features/dashboard/DashboardPage'
import HistoryPage from './features/transactions/HistoryPage'
import TransferPage from './features/transactions/TransferPage'
import AppLayout from './layouts/AppLayout'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'
import ProtectedRoute from './routes/ProtectedRoute'
import RoleRoute from './routes/RoleRoute'

/** The route table (like Laravel's routes/web.php). Nested routes render inside their parent's <Outlet />. */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route path="accounts/:id" element={<AccountDetailPage />} />
          <Route path="transactions" element={<HistoryPage />} />
          <Route path="profile" element={<ProfilePage />} />

          <Route element={<RoleRoute role="CUSTOMER" />}>
            <Route path="transfer" element={<TransferPage />} />
          </Route>

          <Route path="admin" element={<RoleRoute role="ADMIN" />}>
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="audit-logs" element={<AdminAuditLogsPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
