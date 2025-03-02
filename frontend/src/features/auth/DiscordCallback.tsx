import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth/useAuth';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { handleDiscordCallback, handleDiscordCallbackWithToken } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [processingAuth, setProcessingAuth] = useState<boolean>(true);

  useEffect(() => {
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(errorParam);
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    const processAuth = async () => {
      try {
        setProcessingAuth(true);
        const accessToken = searchParams.get('accessToken');
        const refreshToken = searchParams.get('refreshToken') || "";
        
        if (accessToken) {
          // Process token-based authentication
          await handleDiscordCallbackWithToken(accessToken, refreshToken);
          // Wait a moment to ensure token is properly stored before navigation
          setTimeout(() => {
            setProcessingAuth(false);
            navigate('/dashboard');
          }, 300);
          return;
        }

        const code = searchParams.get('code');
        const state = searchParams.get('state');

        if (!code || !state) {
          setError('Missing required OAuth parameters');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Process code-based authentication
        await handleDiscordCallback(code, state);
        // Wait a moment to ensure token is properly stored before navigation
        setTimeout(() => {
          setProcessingAuth(false);
          navigate('/dashboard');
        }, 300);
      } catch (err: any) {
        setError(err.message);
        setProcessingAuth(false);
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    processAuth();
  }, [searchParams, navigate, handleDiscordCallback, handleDiscordCallbackWithToken]);

  if (error) {
    return <div className="auth-error">Authentication failed: {error}</div>;
  }

  return <div className="auth-loading">
    {processingAuth ? "Completing authentication..." : "Authentication complete. Redirecting..."}
  </div>;
} 