import { useLocation, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';

export function AuthErrorPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const error = location.state?.error || 'Authentication failed';

  useEffect(() => {
    const timer = setTimeout(() => {
      navigate('/login');
    }, 5000);

    return () => clearTimeout(timer);
  }, [navigate]);

  return (
    <div className="auth-error-container">
      <div className="auth-error-card">
        <h1>Authentication Error</h1>
        <p>{error}</p>
        <button onClick={() => navigate('/login')}>
          Return to Login
        </button>
      </div>
    </div>
  );
} 