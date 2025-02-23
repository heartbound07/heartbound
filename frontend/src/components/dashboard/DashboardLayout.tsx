import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from './DashboardNavigation';
import '@/assets/dashboard.css';

/**
 * DashboardLayout
 *
 * The outer-most container for the dashboard. It now leverages updated container classes
 * (defined in dashboard.css) for a modern, spacious, and responsive layout.
 */
export function DashboardLayout() {
  return (
    <ProtectedRoute>
      <div className="dashboard-container">
        <DashboardNavigation />
        <main className="dashboard-content overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
