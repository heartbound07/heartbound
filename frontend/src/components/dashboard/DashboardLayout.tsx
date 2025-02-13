import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from './DashboardNavigation';

export function DashboardLayout() {
  return (
    <ProtectedRoute>
      <div className="dashboard-container">
        <DashboardNavigation />
        <main className="dashboard-content">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
