import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      navigate('/login', {
        state: { returnUrl: location.pathname }
      });
    }
  }, [isLoading, isAuthenticated, navigate, location]);

  if (isLoading) {
    return <div className="auth-loading">Loading...</div>;
  }

  return isAuthenticated ? <>{children}</> : null;
} 