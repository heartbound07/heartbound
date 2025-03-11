import { Outlet, useLocation } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '../Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import { useState } from 'react';

/**
 * DashboardLayout
 *
 * The outer-most container for the dashboard. It now leverages updated container classes
 * (defined in dashboard.css) for a modern, spacious, and responsive layout.
 */
export function DashboardLayout() {
  const location = useLocation();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  
  // Check if we're on any dashboard page except valorant
  // This will match dashboard, profile, settings, etc.
  const isDashboardSection = location.pathname.startsWith('/dashboard') && 
                          !location.pathname.includes('/valorant');
  
  return (
    <ProtectedRoute>
      <div className="dashboard-container">
        <DashboardNavigation 
          theme={isDashboardSection ? 'dashboard' : 'default'} 
          onCollapseChange={(collapsed) => setSidebarCollapsed(collapsed)}
        />
        <main className={`dashboard-content ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
