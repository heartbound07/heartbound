import { FC, useEffect, useState, useCallback, useMemo } from 'react';
import { AuthContext } from './AuthContext';
import {
  AuthContextValue,
  AuthState,
  LoginRequest,
  TokenPair,
  UserInfo,
  AuthProviderProps,
  ProfileStatus,
} from './types';
import { AUTH_STORAGE_KEY, TOKEN_REFRESH_MARGIN, AUTH_ENDPOINTS } from './constants';

// Update the type declaration
declare global {
  namespace NodeJS {
    interface Timeout {}
  }
}

const AuthProvider: FC<AuthProviderProps> = ({ children }) => {
  const [state, setState] = useState<AuthState>({
    user: null,
    profile: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,
  });
  const [tokens, setTokens] = useState<TokenPair | null>(null);
  const [refreshTimeout, setRefreshTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);

  const isUserInfo = (data: unknown): data is UserInfo => {
    return !!data && typeof data === 'object' && 'id' in data && 'username' in data;
  };

  const persistAuthState = useCallback((user: UserInfo | null, tokens: TokenPair | null) => {
    try {
      if (user && tokens) {
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user, tokens }));
      } else {
        localStorage.removeItem(AUTH_STORAGE_KEY);
      }
    } catch (error) {
      console.error('Failed to persist auth state:', error);
    }
  }, []);

  const clearAuthState = useCallback(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setState(prev => ({
      ...prev,
      user: null,
      isAuthenticated: false,
      isLoading: false,
    }));
  }, []);

  const scheduleTokenRefresh = useCallback((expiresIn: number) => {
    const refreshTime = expiresIn * 1000 - TOKEN_REFRESH_MARGIN;
    const timeout = setTimeout(() => {
      refreshToken();
    }, refreshTime);
    setRefreshTimeout(timeout);
  }, []);

  const handleAuthResponse = useCallback(async (response: Response) => {
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || 'Authentication failed');
    }

    const data = await response.json();
    if (!data.accessToken || !data.refreshToken || !isUserInfo(data.user)) {
      throw new Error('Invalid authentication response');
    }

    const { accessToken, refreshToken, user } = data;
    const decodedToken = parseJwt(accessToken);
    
    persistAuthState(user, { accessToken, refreshToken });
    scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));

    setState(prev => ({
      ...prev,
      user,
      isAuthenticated: true,
      isLoading: false,
      error: null,
    }));
  }, [persistAuthState, scheduleTokenRefresh]);

  const login = useCallback(async (credentials: LoginRequest) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });
      await handleAuthResponse(response);
    } catch (error) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Login failed',
      }));
      throw error;
    }
  }, [handleAuthResponse]);

  const logout = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true }));
    try {
      await fetch(AUTH_ENDPOINTS.LOGOUT, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${tokens?.accessToken}` },
      });
    } finally {
      clearAuthState();
      if (refreshTimeout) clearTimeout(refreshTimeout);
      setTokens(null);
      setState(prev => ({
        ...prev,
        user: null,
        isAuthenticated: false,
        isLoading: false,
      }));
    }
  }, [clearAuthState, refreshTimeout, tokens]);

  const refreshToken = useCallback(async () => {
    try {
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      if (!storedAuth) return;

      const { refreshToken } = JSON.parse(storedAuth).tokens;
      const response = await fetch(AUTH_ENDPOINTS.REFRESH, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${refreshToken}` },
      });

      await handleAuthResponse(response);
    } catch (error) {
      clearAuthState();
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: 'Session expired. Please log in again.',
      }));
    }
  }, [clearAuthState, handleAuthResponse]);

  const initializeAuth = useCallback(async () => {
    try {
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      if (!storedAuth) {
        setState(prev => ({ ...prev, isLoading: false }));
        return;
      }

      const { user, tokens } = JSON.parse(storedAuth);
      if (!isUserInfo(user) || !tokens?.accessToken) {
        throw new Error('Invalid stored auth data');
      }

      const decodedToken = parseJwt(tokens.accessToken);
      if (decodedToken.exp * 1000 < Date.now()) {
        await refreshToken();
        return;
      }

      scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));
      setTokens(tokens);
      setState(prev => ({
        ...prev,
        user,
        isAuthenticated: true,
        isLoading: false,
      }));
    } catch (error) {
      clearAuthState();
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Session initialization failed',
      }));
    }
  }, [clearAuthState, refreshToken, scheduleTokenRefresh]);

  const startDiscordOAuth = useCallback(async () => {
    window.location.href = AUTH_ENDPOINTS.DISCORD_AUTHORIZE;
  }, []);

  const handleDiscordCallback = useCallback(async (code: string, state: string) => {
    setState(prev => ({ ...prev, isLoading: true, error: null }));
    try {
      const response = await fetch(AUTH_ENDPOINTS.DISCORD_CALLBACK, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, state }),
      });
      await handleAuthResponse(response);
    } catch (error) {
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Discord authentication failed',
      }));
      throw error;
    }
  }, [handleAuthResponse]);

  const handleDiscordCallbackWithToken = useCallback(async (accessToken: string) => {
    // Decode the token using the existing helper
    const decodedToken = parseJwt(accessToken);
    // Extract user info from the decoded token (adjust as needed):
    const user: UserInfo = {
      id: decodedToken.sub || 'unknown',
      username: decodedToken.username || 'unknown',
      email: decodedToken.email || 'unknown',
    };
    // Persist auth state with the received access token; here refreshToken is left as an empty string
    persistAuthState(user, { accessToken, refreshToken: '' });
    scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));
    setState(prev => ({
      ...prev,
      user,
      isAuthenticated: true,
      isLoading: false,
      error: null,
    }));
  }, [persistAuthState, scheduleTokenRefresh]);

  const updateProfile = useCallback((profile: ProfileStatus) => {
    setState(prev => ({
      ...prev,
      profile,
    }));
  }, []);

  useEffect(() => {
    initializeAuth();
    return () => {
      if (refreshTimeout) clearTimeout(refreshTimeout);
    };
  }, []);

  const contextValue = useMemo<AuthContextValue>(() => ({
    ...state,
    login,
    logout,
    register: login,
    refreshToken,
    tokens,
    clearError: () => setState(prev => ({ ...prev, error: null })),
    startDiscordOAuth,
    handleDiscordCallback,
    handleDiscordCallbackWithToken,
    updateProfile,
  }), [state, login, logout, refreshToken, tokens, startDiscordOAuth, handleDiscordCallback, handleDiscordCallbackWithToken, updateProfile]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

function parseJwt(token: string) {
  try {
    return JSON.parse(atob(token.split('.')[1]));
  } catch {
    return { exp: 0 };
  }
}

export { AuthProvider }; 