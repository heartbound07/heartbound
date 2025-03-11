import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '@/components/Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/valorant.css';
import { useState } from 'react';

/**
 * ValorantPageLayout
 *
 * A dedicated layout for the Valorant page. This layout reuses the DashboardNavigation
 * component to maintain consistent auth navigation and wraps the content inside a ProtectedRoute.
 *
 * Now updated to properly handle sidebar collapse state changes.
 */
export function ValorantPageLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    // Initialize from localStorage when component mounts
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });
  
  return (
    <ProtectedRoute>
      <div className="valorant-container">
        <DashboardNavigation onCollapseChange={(collapsed) => setSidebarCollapsed(collapsed)} />
        <main className={`valorant-content ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
