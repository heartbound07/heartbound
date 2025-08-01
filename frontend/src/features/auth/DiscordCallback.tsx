import { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth/useAuth';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { DISCORD_OAUTH_STATE_KEY } from '../../contexts/auth/constants';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { exchangeDiscordCode, fetchCurrentUserProfile } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [processingAuth, setProcessingAuth] = useState<boolean>(true);
  const hasProcessedAuth = useRef(false);

  useEffect(() => {
    // Prevent multiple processing of the same auth callback
    if (hasProcessedAuth.current) {
      console.log('[DiscordCallback] Auth already processed, skipping.');
      return;
    }
    hasProcessedAuth.current = true;

    console.log('[DiscordCallback] Mounted. Current URL:', window.location.href);

    const processAuth = async () => {
      try {
        // Check for error parameter first
        const errorParam = searchParams.get('error');
        console.log('[DiscordCallback] Extracted errorParam from URL:', errorParam);
        if (errorParam) {
          const decodedError = decodeURIComponent(errorParam);
          console.error(`[DiscordCallback] Auth Error: ${decodedError}`);
          setError(`Authentication failed: ${decodedError}`);
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        console.log('[DiscordCallback] Starting auth processing...');

        // Extract parameters from URL
        const code = searchParams.get('code');
        const accessToken = searchParams.get('accessToken');
        const refreshToken = searchParams.get('refreshToken');
        const state = searchParams.get('state');

        console.log(`[DiscordCallback] Extracted params - code: [${code}], accessToken: [${accessToken ? 'present' : 'null'}], refreshToken: [${refreshToken ? 'present' : 'null'}], state: [${state}]`);

        // State validation (common to both flows)
        const storedState = localStorage.getItem(DISCORD_OAUTH_STATE_KEY);
        console.log(`[DiscordCallback] Retrieved storedState from localStorage: [${storedState}]`);

        if (!storedState) {
          const errorMsg = 'No stored OAuth state found. Possible security issue or expired session.';
          console.error(`[DiscordCallback] State Error: ${errorMsg}`);
          setError(errorMsg);
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        if (state !== storedState) {
          const errorMsg = 'OAuth state mismatch. Potential security issue detected.';
          console.error(`[DiscordCallback] State Mismatch: Expected [${storedState}], received [${state}]`);
          setError(errorMsg);
          setProcessingAuth(false);
          localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // State is valid, clear it from localStorage
        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
        console.log('[DiscordCallback] OAuth state validated successfully. Removed from storage.');

        // Check if we have tokens directly (new flow) or need to exchange code (fallback)
        if (accessToken && refreshToken) {
          console.log(`[DiscordCallback] Tokens received directly from backend. Processing...`);
          
          // Create token pair and store directly
          const tokenPair = {
            accessToken: decodeURIComponent(accessToken),
            refreshToken: decodeURIComponent(refreshToken),
            tokenType: 'bearer',
            expiresIn: 1800, // 30 minutes, matching backend configuration
            scope: 'identify guilds'
          };
          
          // Import the tokenStorage directly to store tokens
          const { tokenStorage } = await import('../../contexts/auth/tokenStorage');
          tokenStorage.setTokens(tokenPair);
          
          console.log('[DiscordCallback] Tokens stored successfully. Waiting for state sync...');
          
          // Small delay to ensure token state is synchronized across all components
          await new Promise(resolve => setTimeout(resolve, 500));
          
          console.log('[DiscordCallback] State sync completed. Fetching user profile...');
          
          // Now fetch the user profile to properly initialize the auth state
          try {
            await fetchCurrentUserProfile();
            console.log('[DiscordCallback] User profile fetched successfully. Auth state updated.');
            
            // Small delay to ensure all state updates and WebSocket subscriptions are ready
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            setProcessingAuth(false);
            console.log('[DiscordCallback] Navigation to dashboard initiated.');
            navigate('/dashboard');
          } catch (profileError: any) {
            console.error('[DiscordCallback] Error fetching user profile after token storage:', profileError);
            setError('Failed to initialize user session after authentication.');
            setProcessingAuth(false);
            setTimeout(() => navigate('/login'), 3000);
          }
          
        } else if (code) {
          console.log(`[DiscordCallback] Using legacy code exchange flow...`);
          await exchangeDiscordCode(code);
          setProcessingAuth(false);
          console.log('[DiscordCallback] exchangeDiscordCode call successful. Navigating to dashboard.');
          navigate('/dashboard');
        } else {
          const errorMsg = 'Missing required authentication parameters in URL after state validation.';
          console.error(`[DiscordCallback] Parameter Error: ${errorMsg}`);
          setError(errorMsg);
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

      } catch (err: any) {
        const errorMsg = `Authentication failed: ${err?.message || 'An unknown error occurred.'}`;
        console.error(`[DiscordCallback] Auth Error: ${errorMsg}`, err);
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

  }, [searchParams, navigate, exchangeDiscordCode, fetchCurrentUserProfile]);

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