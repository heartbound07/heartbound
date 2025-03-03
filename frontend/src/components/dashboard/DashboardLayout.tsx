import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from './DashboardNavigation';
import '@/assets/dashboard.css';
import '@/assets/animations.css';

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
        {/* Enhanced backdrop effects */}
        <div className="absolute inset-0 bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] opacity-90"></div>
        
        {/* Subtle animated background elements */}
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute -top-1/4 -right-1/4 w-1/2 h-1/2 rounded-full bg-white/5 blur-3xl"></div>
          <div className="absolute -bottom-1/4 -left-1/4 w-1/2 h-1/2 rounded-full bg-white/5 blur-3xl"></div>
        </div>
        
        {/* Main layout structure */}
        <div className="relative z-10 flex min-h-screen">
          <DashboardNavigation />
          <main className="dashboard-content overflow-y-auto w-full">
            <Outlet />
          </main>
        </div>
      </div>
    </ProtectedRoute>
  );
}
