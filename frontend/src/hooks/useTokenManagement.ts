import { useState, useCallback, useRef, useEffect } from 'react';
import { TokenPair } from '../contexts/auth/types';
import { tokenStorage } from '../contexts/auth/tokenStorage';
import { TOKEN_REFRESH_MARGIN } from '../contexts/auth/constants';

export function useTokenManagement() {
  const [tokens, setTokens] = useState<TokenPair | null>(tokenStorage.getTokens());
  const [refreshTimeout, setRefreshTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);
  
  // Reference to refreshToken function for setTimeout
  const refreshTokenRef = useRef<(() => Promise<string | undefined>)>();

  // Add effect to sync tokens state with storage changes
  useEffect(() => {
    const syncTokensWithStorage = () => {
      const storedTokens = tokenStorage.getTokens();
      setTokens(storedTokens);
    };

    // Initial sync
    syncTokensWithStorage();

    // Set up polling to detect storage changes (for cross-component sync)
    const syncInterval = setInterval(syncTokensWithStorage, 1000);

    return () => {
      clearInterval(syncInterval);
    };
  }, []);

  // Parse JWT token to extract payload
  const parseJwt = useCallback((token: string | undefined) => {
    try {
      // Check if token exists and is not empty
      if (!token) {
        return null;
      }
      
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.warn('Invalid JWT token format');
        return null;
      }
      
      const base64Url = parts[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      console.error('Error parsing JWT token:', error);
      return null;
    }
  }, []);

  // Validate token is not expired and properly formatted
  const isTokenValid = useCallback((token: string | undefined) => {
    if (!token) return false;
    
    const decoded = parseJwt(token);
    if (!decoded || !decoded.exp) return false;
    
    const currentTime = Math.floor(Date.now() / 1000);
    return decoded.exp > currentTime;
  }, [parseJwt]);

  // Schedule token refresh
  const scheduleTokenRefresh = useCallback((expiresIn: number) => {
    const refreshTime = expiresIn * 1000 - TOKEN_REFRESH_MARGIN;
    
    // Clear any existing timeout
    if (refreshTimeout) {
      clearTimeout(refreshTimeout);
    }
    
    // Correctly define the timeout
    const timeout = setTimeout(() => {
      if (refreshTokenRef.current) {
        refreshTokenRef.current();
      }
    }, refreshTime > 0 ? refreshTime : 0);
    
    setRefreshTimeout(timeout);
  }, []);

  // Update tokens in state and storage
  const updateTokens = useCallback((tokenPair: TokenPair | null) => {
    console.log('[TokenManagement] Updating tokens:', tokenPair ? 'present' : 'null');
    tokenStorage.setTokens(tokenPair);
    setTokens(tokenPair);
    
    // Schedule refresh if we have new tokens
    if (tokenPair?.accessToken) {
      const decodedToken = parseJwt(tokenPair.accessToken);
      if (decodedToken?.exp) {
        const expiresIn = decodedToken.exp - Math.floor(Date.now() / 1000);
        scheduleTokenRefresh(expiresIn);
      }
    }
  }, [parseJwt, scheduleTokenRefresh]);

  // Clear tokens on unmount
  useEffect(() => {
    return () => {
      if (refreshTimeout) {
        clearTimeout(refreshTimeout);
      }
    };
  }, []);

  return {
    tokens,
    parseJwt,
    updateTokens,
    scheduleTokenRefresh,
    refreshTokenRef,
    isTokenValid,
  };
}
