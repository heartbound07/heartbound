import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth/useAuth';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

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
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#111827] to-[#1f2937] text-white font-sans flex items-center justify-center">
        <div className="p-8 rounded-xl bg-[#1a1b1e]/60 backdrop-blur-sm border border-white/5 shadow-lg text-center">
          <div className="text-5xl font-bold text-[#FF4655] mb-4">Error</div>
          <div className="text-xl font-medium text-white/90 mb-2">Authentication Failed</div>
          <div className="text-sm text-white/50 mb-6">{error}</div>
        </div>
      </div>
    );
  }

  return (
    <LoadingSpinner
      title={processingAuth ? "Completing authentication..." : "Authentication complete!"}
      description={processingAuth ? "Please wait while we log you in..." : "Redirecting you to dashboard..."}
      fullScreen={true}
      theme="dashboard"
    />
  );
} 