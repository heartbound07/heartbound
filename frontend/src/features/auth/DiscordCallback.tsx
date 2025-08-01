import { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth/useAuth';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { DISCORD_OAUTH_STATE_KEY } from '../../contexts/auth/constants';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { exchangeDiscordCode, setTokens } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [processingAuth, setProcessingAuth] = useState<boolean>(true);
  const hasProcessedAuth = useRef(false);

  useEffect(() => {
    // Log the full URL as soon as the component mounts
    console.log('[DiscordCallback] Mounted. Current URL:', window.location.href);

    if (hasProcessedAuth.current) {
      console.log('[DiscordCallback] Auth already processed, skipping.');
      return;
    }

    const errorParam = searchParams.get('error');
    console.log('[DiscordCallback] Extracted errorParam from URL:', errorParam);
    if (errorParam) {
      const decodedError = decodeURIComponent(errorParam.replace(/\+/g, ' '));
      console.error(`[DiscordCallback] Received error from backend redirect: ${decodedError}`);
      setError(`Authentication failed: ${decodedError}`);
      setProcessingAuth(false);
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    const processAuth = async () => {
      try {
        hasProcessedAuth.current = true;
        console.log('[DiscordCallback] Starting auth processing...');

        const code = searchParams.get('code');
        const state = searchParams.get('state');
        console.log(`[DiscordCallback] Extracted params - code: [${code}], state: [${state}]`);

        const storedState = localStorage.getItem(DISCORD_OAUTH_STATE_KEY);
        console.log(`[DiscordCallback] Retrieved storedState from localStorage: [${storedState}]`);

        if (!state || !storedState) {
          const errorMsg = `OAuth state parameter missing or not found in storage. Cannot verify request origin. Received state: [${state}], Stored state: [${storedState}]`;
          console.error(`[DiscordCallback] State Validation Error: ${errorMsg}`);
          setError(errorMsg);
          setProcessingAuth(false);
          localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        if (state !== storedState) {
          const errorMsg = `OAuth state mismatch. Possible CSRF attack detected. Received: [${state}], Expected: [${storedState}].`;
          console.error(`[DiscordCallback] State Validation Error: ${errorMsg}`);
          setError(errorMsg + ' Please try logging in again.');
          setProcessingAuth(false);
          localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
        console.log('[DiscordCallback] OAuth state validated successfully. Removed from storage.');

        // Check for direct token parameters (new approach)
        const accessToken = searchParams.get('accessToken');
        const refreshToken = searchParams.get('refreshToken');
        
        if (!accessToken || !refreshToken) {
          const errorMsg = 'Missing required token parameters in URL after state validation.';
          console.error(`[DiscordCallback] Token Error: ${errorMsg}`);
          setError(errorMsg);
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        console.log(`[DiscordCallback] State validated. Processing tokens directly...`);
        
        // Store tokens directly using the auth context
        const tokenPair = {
          accessToken: decodeURIComponent(accessToken),
          refreshToken: decodeURIComponent(refreshToken),
          tokenType: 'bearer',
          expiresIn: 1800, // 30 minutes, matching backend configuration
          scope: 'identify guilds'
        };
        
        setTokens(tokenPair);

        setProcessingAuth(false);
        console.log('[DiscordCallback] exchangeDiscordCode call successful. Navigating to dashboard.');
        navigate('/dashboard');

      } catch (err: any) {
        const errorMsg = `Authentication failed during exchangeDiscordCode call: ${err?.message || 'An unknown error occurred.'}`;
        console.error(`[DiscordCallback] Exchange Error: ${errorMsg}`, err);
        setError(errorMsg);
        setProcessingAuth(false);
        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY); // Clean up state on error
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    // Delay slightly to ensure URL parameters are definitely available
    const timeoutId = setTimeout(processAuth, 50);

    // Cleanup function for the timeout
    return () => clearTimeout(timeoutId);

  }, [searchParams, navigate, exchangeDiscordCode, setTokens]);

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