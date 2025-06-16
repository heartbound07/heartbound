import { Outlet } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardNavigation } from '@/components/Sidebar';
import '@/assets/animations.css';
import '@/assets/PairingsPage.css';
import '@/assets/theme.css';
import { useState, useEffect } from 'react';
import { useTheme } from '@/contexts/ThemeContext';

/**
 * PairingsPageLayout
 *
 * A dedicated layout for the Pairings page. This layout reuses the DashboardNavigation
 * component to maintain consistent auth navigation and wraps the content inside a ProtectedRoute.
 *
 * Follows the same pattern as ValorantPageLayout with proper sidebar collapse state management
 * and CSS isolation through the .pairings-page-wrapper strategy.
 */
export function PairingsPageLayout() {
  const { theme } = useTheme();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    // Initialize from localStorage when component mounts
    const savedState = localStorage.getItem('sidebar-collapsed');
    return savedState ? JSON.parse(savedState) : false;
  });
  
  // Apply overflow control for mobile view if needed
  useEffect(() => {
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
      <div className={`pairings-container theme-${theme}`}>
        <DashboardNavigation 
          theme={theme === 'dark' ? 'default' : 'dashboard'}
          onCollapseChange={(collapsed) => setSidebarCollapsed(collapsed)} 
        />
        <main className={`pairings-content ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
          <Outlet />
        </main>
      </div>
    </ProtectedRoute>
  );
} 