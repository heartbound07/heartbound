import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '@/components/Sidebar';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/valorant.css';
import '@/assets/theme.css';
import { useState, useEffect } from 'react';
import { useTheme } from '@/contexts/ThemeContext';

/**
 * ValorantPageLayout
 *
 * A dedicated layout for the Valorant page. This layout reuses the DashboardNavigation
 * component to maintain consistent auth navigation and wraps the content inside a ProtectedRoute.
 *
 * Now updated to properly handle sidebar collapse state changes and mobile overflow.
 */
export function ValorantPageLayout() {
  const { theme } = useTheme();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    // Initialize from localStorage when component mounts
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });
  
  // Add effect to set overflow hidden on body for mobile
  useEffect(() => {
    // Apply overflow control for mobile view
    const handleResize = () => {
      if (window.innerWidth < 768) {
        document.body.style.overflow = 'hidden';
      } else {
        document.body.style.overflow = '';
      }
    };
    
    // Set initial state
    handleResize();
    
    // Listen for resize events
    window.addEventListener('resize', handleResize);
    
    // Cleanup
    return () => {
      window.removeEventListener('resize', handleResize);
      document.body.style.overflow = '';
    };
  }, []);
  
  return (
    <ProtectedRoute>
      <div className={`valorant-container theme-${theme}`}>
        <DashboardNavigation 
          theme={theme === 'dark' ? 'default' : 'dashboard'}
          onCollapseChange={(collapsed) => setSidebarCollapsed(collapsed)} 
        />
        <main className={`valorant-content ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
}
