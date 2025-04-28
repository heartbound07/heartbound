import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '../Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/theme.css';
import { useState } from 'react';
import { useTheme } from '@/contexts/ThemeContext';

/**
 * DashboardLayout
 *
 * The outer-most container for the dashboard. It now leverages updated container classes
 * (defined in dashboard.css) for a modern, spacious, and responsive layout.
 */
export function DashboardLayout() {
  const { theme } = useTheme();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    // Initialize from localStorage when component mounts
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });
  
  // Check if we're on any dashboard page except valorant
  // This will match dashboard, profile, settings, etc.
  
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
