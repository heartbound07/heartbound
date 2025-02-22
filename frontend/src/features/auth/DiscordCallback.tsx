import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleDiscordCallbackWithToken } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(errorParam);
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    const token = searchParams.get('token');
    if (!token) {
      setError('Missing token parameter');
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    handleDiscordCallbackWithToken(token)
      .then(() => {
        navigate('/dashboard');
      })
      .catch((err) => {
        setError(err.message);
        setTimeout(() => navigate('/login'), 3000);
      });
  }, [searchParams, navigate, handleDiscordCallbackWithToken]);

  if (error) {
    return <div className="auth-error">Authentication failed: {error}</div>;
  }

  return <div className="auth-loading">Completing authentication...</div>;
} 