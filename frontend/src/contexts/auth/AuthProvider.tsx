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
import * as partyService from '../valorant/partyService';
import axios from 'axios';

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

  const parseJwt = (token: string) => {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        console.error('JWT does not have 3 parts:', token);
        return { exp: 0 };
      }
      const base64UrlPayload = parts[1];
      console.log('Raw base64Url payload:', base64UrlPayload);
      // Convert from base64url to base64
      let base64 = base64UrlPayload.replace(/-/g, '+').replace(/_/g, '/');
      while (base64.length % 4) {
        base64 += '=';
      }
      const decodedPayload = atob(base64);
      console.log('Decoded payload string:', decodedPayload);
      return JSON.parse(decodedPayload);
    } catch (error) {
      console.error('Error while decoding JWT:', error);
      return { exp: 0 };
    }
  };

  const handleAuthResponse = useCallback(async (response: Response) => {
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || 'Authentication failed');
    }
    const data = await response.json();
    // Ensure full token pair is returned from the backend
    if (
      !data.accessToken ||
      !data.refreshToken ||
      !data.tokenType ||
      !data.expiresIn ||
      !data.scope ||
      !isUserInfo(data.user)
    ) {
      throw new Error('Invalid authentication response');
    }
    const { accessToken, refreshToken, tokenType, expiresIn, scope, user } = data;
    const decodedToken = parseJwt(accessToken);
    persistAuthState(user, { accessToken, refreshToken, tokenType, expiresIn, scope });
    scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));
    setTokens({ accessToken, refreshToken, tokenType, expiresIn, scope });
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
      const response = await fetch(AUTH_ENDPOINTS.LOGIN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });
      await handleAuthResponse(response);
    } catch (error) {
      clearAuthState();
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: error instanceof Error ? error.message : 'Session initialization failed',
      }));
      throw error;
    }
  }, [clearAuthState, handleAuthResponse]);

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
    if (!tokens?.refreshToken) {
      throw new Error("No refresh token found");
    }
    try {
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken: tokens.refreshToken });
      const data: TokenPair = response.data;
      setTokens(data);
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      if (storedAuth) {
        const parsed = JSON.parse(storedAuth);
        persistAuthState(parsed.user, data);
      }
    } catch (error: any) {
      console.error("Failed to refresh token:", error);
      // Optionally call logout if refresh fails
    }
  }, [tokens, persistAuthState]);

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
    console.log('Raw accessToken from URL:', accessToken);
    const decodedTokenString = decodeURIComponent(accessToken);
    console.log('Decoded token string after decodeURIComponent:', decodedTokenString);
    const decodedToken = parseJwt(decodedTokenString);
    console.log('Final decoded JWT object:', decodedToken);
    const user: UserInfo = {
      id: decodedToken.sub || 'unknown',
      username: decodedToken.username || 'unknown',
      email: decodedToken.email || 'unknown',
      avatar: decodedToken.avatar || '/default-avatar.png',
    };
    // In cases when only an access token is provided via URL,
    // we create a minimal TokenPair using defaults
    const expiresIn = decodedToken.exp - Math.floor(Date.now() / 1000);
    const tokenPair: TokenPair = {
      accessToken: decodedTokenString,
      refreshToken: "", // default empty if not provided via URL
      tokenType: "bearer",
      expiresIn: expiresIn,
      scope: ""
    };
    persistAuthState(user, tokenPair);
    scheduleTokenRefresh(expiresIn);
    setTokens(tokenPair);
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
    createParty: partyService.createParty,
    getParty: partyService.getParty,
    listParties: partyService.listParties,
    updateParty: partyService.updateParty,
    deleteParty: partyService.deleteParty,
    joinParty: partyService.joinParty,
  }), [state, login, logout, tokens, startDiscordOAuth, handleDiscordCallback, handleDiscordCallbackWithToken, updateProfile, refreshToken]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export { AuthProvider }; 