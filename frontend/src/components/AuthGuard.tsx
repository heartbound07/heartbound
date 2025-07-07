import React from 'react';
import { useLocation, Navigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { useTheme } from '@/contexts/ThemeContext';
import { requiresAuth } from '@/utils/routeGuard';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const { isAuthenticated, isLoading } = useAuth();
  const { theme } = useTheme();
  const location = useLocation();

  if (isLoading) {
    return (
      <LoadingSpinner
        title="Authenticating..."
        description="Please wait..."
        fullScreen={true}
        useSkeleton={false}
        theme={theme === 'dark' ? 'valorant' : 'dashboard'}
      />
    );
  }

  if (requiresAuth(location.pathname) && !isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ returnUrl: location.pathname }}
      />
    );
  }

  return <>{children}</>;
} 