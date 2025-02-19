import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleDiscordCallback, handleDiscordCallbackWithToken } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(errorParam);
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    const accessToken = searchParams.get('accessToken');
    if (accessToken) {
      handleDiscordCallbackWithToken(accessToken)
        .then(() => {
          navigate('/dashboard');
        })
        .catch((err) => {
          setError(err.message);
          setTimeout(() => navigate('/login'), 3000);
        });
      return;
    }

    const code = searchParams.get('code');
    const state = searchParams.get('state');

    if (!code || !state) {
      setError('Missing required OAuth parameters');
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    handleDiscordCallback(code, state)
      .then(() => {
        navigate('/dashboard');
      })
      .catch((err) => {
        setError(err.message);
        setTimeout(() => navigate('/login'), 3000);
      });
  }, [searchParams, navigate, handleDiscordCallback, handleDiscordCallbackWithToken]);

  if (error) {
    return <div className="auth-error">Authentication failed: {error}</div>;
  }

  return <div className="auth-loading">Completing authentication...</div>;
} 