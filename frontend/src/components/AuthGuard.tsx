import React from 'react';
import { useLocation, Navigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { requiresAuth } from '@/utils/routeGuard';

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <div className="auth-loading">Loading...</div>;
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