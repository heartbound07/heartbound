import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '@/components/dashboard/DashboardNavigation';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/valorant.css';

/**
 * ValorantPageLayout
 *
 * A dedicated layout for the Valorant page. This layout reuses the DashboardNavigation
 * component to maintain consistent auth navigation and wraps the content inside a ProtectedRoute.
 *
 * You can apply custom styling with the "valorant-container" and "valorant-content" classes.
 */
export function ValorantPageLayout() {
  return (
    <ProtectedRoute>
      <div className="valorant-container">
        <DashboardNavigation />
        <main className="valorant-content overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
