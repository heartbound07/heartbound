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
    console.log('[DiscordCallback] useEffect triggered. Current URL:', window.location.href);
    console.log('[DiscordCallback] Initial hasProcessedAuth.current:', hasProcessedAuth.current);
    
    // Temporarily reset to debug re-mounting issues
    hasProcessedAuth.current = false;
    
    // Prevent multiple processing of the same auth callback
    if (hasProcessedAuth.current) {
      console.log('[DiscordCallback] Auth already processed, skipping.');
      return;
    }

    console.log('[DiscordCallback] Mounted. Current URL:', window.location.href);
    console.log('[DiscordCallback] hasProcessedAuth.current:', hasProcessedAuth.current);
    
    // Mark as processed early to prevent race conditions
    hasProcessedAuth.current = true;

    const processAuth = async () => {
      try {
        console.log('[DiscordCallback] Starting authentication processing...');
        
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
        const state = searchParams.get('state');

        console.log(`[DiscordCallback] Extracted params - code: [${code ? 'present' : 'null'}], state: [${state}]`);

        // State validation
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

        // Process authentication using secure code exchange flow
        if (code) {
          console.log(`[DiscordCallback] Processing authentication with secure code exchange...`);
          console.log(`[DiscordCallback] About to call exchangeDiscordCode with code: ${code}`);
          
          await exchangeDiscordCode(code);
          
          console.log('[DiscordCallback] exchangeDiscordCode call completed successfully.');
          setProcessingAuth(false);
          console.log('[DiscordCallback] Navigating to dashboard...');
          navigate('/dashboard');
        } else {
          const errorMsg = 'Missing required authentication code in URL after state validation.';
          console.error(`[DiscordCallback] Parameter Error: ${errorMsg}`);
          setError(errorMsg);
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

      } catch (err: any) {
        console.error('[DiscordCallback] Exception in processAuth:', err);
        const errorMsg = `Authentication failed: ${err?.message || 'An unknown error occurred.'}`;
        console.error(`[DiscordCallback] Auth Error: ${errorMsg}`, err);
        setError(errorMsg);
        setProcessingAuth(false);
        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY); // Clean up state on error
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    // Delay slightly to ensure URL parameters are definitely available
    console.log('[DiscordCallback] Setting timeout to start processAuth...');
    const timeoutId = setTimeout(() => {
      console.log('[DiscordCallback] Timeout triggered, calling processAuth...');
      processAuth();
    }, 50);

    // Cleanup function for the timeout
    return () => {
      console.log('[DiscordCallback] Cleaning up timeout...');
      clearTimeout(timeoutId);
    };

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