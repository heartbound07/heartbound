import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '@/components/Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/theme.css';
import { useState } from 'react';
import { useTheme } from '@/contexts/ThemeContext';

/**
 * ShopLayout
 *
 * A dedicated layout for the Shop page. This layout reuses the DashboardNavigation
 * component to maintain consistent navigation and wraps the content inside a ProtectedRoute.
 */
export function ShopLayout() {
  const { theme } = useTheme();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    // Initialize from localStorage when component mounts
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });
  
  return (
    <ProtectedRoute>
      <div className={`dashboard-container theme-${theme}`}>
        <DashboardNavigation 
          theme={theme === 'dark' ? 'default' : 'dashboard'} 
          onCollapseChange={(collapsed) => setSidebarCollapsed(collapsed)}
        />
        <main className={`dashboard-content ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
