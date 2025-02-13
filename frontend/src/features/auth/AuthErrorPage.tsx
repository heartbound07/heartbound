import { useLocation, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { AUTH_ERRORS } from '@/contexts/auth/constants';

export function AuthErrorPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const error = location.state?.error || AUTH_ERRORS.UNAUTHORIZED;

  useEffect(() => {
    const timer = setTimeout(() => {
      navigate('/login');
    }, 5000);

    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1>Authentication Error</h1>
        <p>{error}</p>
        <button onClick={() => navigate('/login')}>
          Return to Login
        </button>
      </div>
    </div>
  );
} 