import { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/auth/useAuth';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';
import { DISCORD_OAUTH_STATE_KEY } from '../../contexts/auth/constants';

export function DiscordCallback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { exchangeDiscordCode } = useAuth(); // Use the new function
  const [error, setError] = useState<string | null>(null);
  const [processingAuth, setProcessingAuth] = useState<boolean>(true);
  // Use a ref to track if we've already processed the auth
  const hasProcessedAuth = useRef(false);

  useEffect(() => {
    // If we've already processed auth, don't do it again
    if (hasProcessedAuth.current) return;
    
    // Check for errors from the backend redirect first
    const errorParam = searchParams.get('error');
    if (errorParam) {
      setError(`Authentication failed: ${errorParam.replace(/\+/g, ' ')}`);
      setProcessingAuth(false);
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    const processAuth = async () => {
      try {
        // Mark that we've started processing
        hasProcessedAuth.current = true;

        // Get code and state from URL
        const code = searchParams.get('code');
        const state = searchParams.get('state');

        // Retrieve the state stored before redirect
        const storedState = localStorage.getItem(DISCORD_OAUTH_STATE_KEY);

        // --- Security Check: Validate State ---
        if (!state || !storedState) {
          setError('OAuth state parameter missing. Cannot verify request origin.');
          setProcessingAuth(false);
          localStorage.removeItem(DISCORD_OAUTH_STATE_KEY); // Clean up potentially invalid state
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        if (state !== storedState) {
          setError('OAuth state mismatch. Possible CSRF attack detected. Please try logging in again.');
          setProcessingAuth(false);
          localStorage.removeItem(DISCORD_OAUTH_STATE_KEY); // Clean up
          setTimeout(() => navigate('/login'), 3000);
          return;
        }
        // State is valid, remove it from storage
        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
        console.log('OAuth state validated successfully.');
        // --- End Security Check ---

        if (!code) {
          setError('Missing required OAuth code parameter.');
          setProcessingAuth(false);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Exchange the code for tokens via the AuthProvider
        await exchangeDiscordCode(code);

        // Wait a moment to ensure token is properly stored before navigation
        setProcessingAuth(false); // Mark processing as complete
        console.log('Code exchange successful, navigating to dashboard.');
        navigate('/dashboard'); // Navigate immediately after success

      } catch (err: any) {
        setError(`Authentication failed: ${err.message || 'An unknown error occurred.'}`);
        setProcessingAuth(false);
        localStorage.removeItem(DISCORD_OAUTH_STATE_KEY); // Clean up state on error
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    processAuth();
  }, [searchParams, navigate, exchangeDiscordCode]); // Update dependencies

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