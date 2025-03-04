import { Outlet, useLocation } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '../Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';

/**
 * DashboardLayout
 *
 * The outer-most container for the dashboard. It now leverages updated container classes
 * (defined in dashboard.css) for a modern, spacious, and responsive layout.
 */
export function DashboardLayout() {
  const location = useLocation();
  
  // Check if we're on any dashboard page except valorant
  // This will match dashboard, profile, settings, etc.
  const isDashboardSection = location.pathname.startsWith('/dashboard') && 
                          !location.pathname.includes('/valorant');
  
  return (
    <ProtectedRoute>
      <div className="dashboard-container">
        <DashboardNavigation theme={isDashboardSection ? 'dashboard' : 'default'} />
        <main className="dashboard-content">
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
