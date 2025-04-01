import { FC, useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { AuthContext } from './AuthContext';
import {
  AuthContextValue,
  AuthState,
  LoginRequest,
  TokenPair,
  UserInfo,
  AuthProviderProps,
  ProfileStatus,
  Role,
} from './types';
import { AUTH_STORAGE_KEY, TOKEN_REFRESH_MARGIN, AUTH_ENDPOINTS } from './constants';
import * as partyService from '../valorant/partyService';
import axios from 'axios';
import webSocketService from '../../config/WebSocketService';
import * as userService from '../../config/userService';
import { UpdateProfileDTO } from '@/config/userService';
import { tokenStorage } from './tokenStorage';

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
  const [tokens, setTokens] = useState<TokenPair | null>(tokenStorage.getTokens());
  const [refreshTimeout, setRefreshTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);

  // First, update the ref type to explicitly allow the string return
  const refreshTokenRef = useRef<(() => Promise<string | undefined>)>();

  const isUserInfo = (data: unknown): data is UserInfo => {
    return !!data && typeof data === 'object' && 'id' in data && 'username' in data;
  };

  const persistAuthState = useCallback((user: UserInfo | null, tokenPair: TokenPair | null) => {
    try {
      if (user && tokenPair) {
        // Only store user info in localStorage, tokens stay in memory
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user }));
        // Update in-memory tokens using tokenStorage
        tokenStorage.setTokens(tokenPair);
        setTokens(tokenPair);
      } else {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        tokenStorage.clearTokens();
        setTokens(null);
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

  // Define scheduleTokenRefresh without depending on refreshToken directly
  const scheduleTokenRefresh = useCallback((expiresIn: number) => {
    const refreshTime = expiresIn * 1000 - TOKEN_REFRESH_MARGIN;
    const timeout = setTimeout(() => {
      // Use the ref's current value, which will be set later
      if (refreshTokenRef.current) {
        refreshTokenRef.current();
      }
    }, refreshTime);
    setRefreshTimeout(timeout);
  }, []);

  const parseJwt = (token: string) => {
    try {
      const base64Url = token.split('.')[1];
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
      if (tokens?.accessToken) {
        await fetch(AUTH_ENDPOINTS.LOGOUT, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${tokens.accessToken}` },
        });
      }
    } catch (error) {
      console.error("Logout request failed:", error);
      // Continue with local logout even if server logout fails
    } finally {
      if (refreshTimeout) {
        clearTimeout(refreshTimeout);
        setRefreshTimeout(null);
      }
      clearAuthState();
      setTokens(null);
      setState(prev => ({
        ...prev,
        user: null,
        isAuthenticated: false,
        isLoading: false,
      }));
    }
  }, [clearAuthState, refreshTimeout, tokens]);

  // Then ensure refreshToken returns the token
  const refreshToken = useCallback(async () => {
    if (!tokens?.refreshToken) {
      console.error("No refresh token found");
      await logout();
      return undefined;
    }
    
    try {
      console.log("Refreshing access token...");
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken: tokens.refreshToken });
      console.log("Raw token response:", response.data);
      
      // Handle potential response format differences
      let tokenResponse: TokenPair;
      
      // Check if the response matches our expected structure
      if (response.data.accessToken && response.data.refreshToken) {
        tokenResponse = response.data;
      } 
      // Sometimes backend might wrap the token in another object
      else if (response.data.tokens && response.data.tokens.accessToken) {
        tokenResponse = response.data.tokens;
      }
      // Backend might return a different structure, adapt accordingly
      else if (typeof response.data === 'object') {
        // Try to adapt the response to our TokenPair structure
        tokenResponse = {
          accessToken: response.data.accessToken || response.data.access_token,
          refreshToken: response.data.refreshToken || response.data.refresh_token,
          tokenType: response.data.tokenType || response.data.token_type || "bearer",
          expiresIn: response.data.expiresIn || response.data.expires_in || 3600,
          scope: response.data.scope || ""
        };
      } else {
        throw new Error("Unexpected response format from server");
      }
      
      if (!tokenResponse.accessToken) {
        throw new Error("No access token in response");
      }
      
      // Extract user info from the token to maintain consistency
      const decodedToken = parseJwt(tokenResponse.accessToken);
      
      // Update tokens in state
      setTokens(tokenResponse);
      
      // Get stored user data
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      if (storedAuth) {
        const parsed = JSON.parse(storedAuth);
        if (isUserInfo(parsed.user)) {
          // Persist the updated tokens with the existing user info
          persistAuthState(parsed.user, tokenResponse);
          
          // Calculate new expiration time and schedule refresh
          const newExpiresIn = decodedToken.exp - Math.floor(Date.now() / 1000);
          if (refreshTimeout) clearTimeout(refreshTimeout);
          scheduleTokenRefresh(newExpiresIn);
          
          console.log("Token refresh successful, next refresh scheduled");
          
          // Add this section: Notify WebSocketService to reconnect with the fresh token
          // Small delay ensures localStorage is properly updated first
          setTimeout(() => {
            webSocketService.reconnectWithFreshToken();
          }, 300);
        }
      }
      
      return tokenResponse.accessToken;
    } catch (error: any) {
      console.error("Failed to refresh token:", error);
      console.error("Error details:", error.response?.data || error.message);
      // If refresh fails, log the user out as their session is no longer valid
      await logout();
      throw new Error(error.response?.data?.message || "Session expired. Please login again.");
    }
  }, [tokens, persistAuthState, scheduleTokenRefresh, logout]);

  // Set the ref to the function after it's defined
  useEffect(() => {
    refreshTokenRef.current = refreshToken;
  }, [refreshToken]);

  const initializeAuth = useCallback(async () => {
    try {
      if (tokenStorage.hasStoredAuthStatus()) {
        const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
        if (storedAuth) {
          const parsed = JSON.parse(storedAuth);
          // Get tokens from memory instead of localStorage
          const currentTokens = tokenStorage.getTokens();
          
          if (isUserInfo(parsed.user) && currentTokens) {
            // Set authentication state
            setState({
              user: parsed.user,
              profile: parsed.profile || null,
              isAuthenticated: true,
              isLoading: false,
              error: null
            });
            
            // Schedule token refresh based on expiry
            scheduleTokenRefresh(currentTokens.expiresIn);
          } else {
            // Missing tokens in memory even though auth status exists
            await logout();
          }
        }
      }
      setState(prev => ({ ...prev, isLoading: false }));
    } catch (error) {
      console.error('Error initializing auth:', error);
      setState({
        user: null,
        profile: null,
        isAuthenticated: false,
        isLoading: false,
        error: 'Failed to initialize authentication'
      });
    }
  }, [logout, scheduleTokenRefresh]);

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

  const handleDiscordCallbackWithToken = useCallback(async (accessToken: string, refreshToken?: string) => {
    try {
      setState(prev => ({ ...prev, isLoading: true }));

      // Decode JWT token to get user information
      const decodedToken = parseJwt(accessToken);
      
      if (!decodedToken) {
        throw new Error('Invalid token format');
      }

      // Extract user data from the token
      const user: UserInfo = {
        id: decodedToken.sub || '',
        username: decodedToken.username || '',
        email: decodedToken.email || '',
        avatar: decodedToken.avatar || '',
        roles: decodedToken.roles || ['USER'], // Extract roles from token
        credits: decodedToken.credits || 0 // Extract credits from token
      };

      // Build token pair
      const tokenPair: TokenPair = {
        accessToken,
        refreshToken: refreshToken || '',
        tokenType: 'bearer',
        expiresIn: decodedToken.exp ? (decodedToken.exp * 1000 - Date.now()) / 1000 : 3600,
        scope: decodedToken.scope || 'identify email'
      };

      // Store auth data
      persistAuthState(user, tokenPair);
      
      // Set auth state
      setState({
        user,
        profile: null, // Will be loaded later
        isAuthenticated: true,
        isLoading: false,
        error: null
      });
      
      // Setup token refresh timer
      scheduleTokenRefresh(tokenPair.expiresIn * 1000 - TOKEN_REFRESH_MARGIN);
      
      // Reconnect websocket with new token
      webSocketService.reconnectWithFreshToken();
      
      return user;
    } catch (error) {
      setState(prev => ({ 
        ...prev, 
        isLoading: false, 
        error: error instanceof Error ? error.message : 'Authentication failed' 
      }));
      throw error;
    }
  }, [persistAuthState, scheduleTokenRefresh]);

  const updateProfile = useCallback((profile: ProfileStatus) => {
    setState(prev => ({
      ...prev,
      profile,
    }));
  }, []);

  const updateUserProfile = async (profile: UpdateProfileDTO) => {
    if (!state.user?.id) {
      throw new Error('User not authenticated');
    }
    
    try {
      setState(prev => ({ ...prev, isLoading: true, error: null }));
      const updatedProfile = await userService.updateUserProfile(state.user!.id, profile);
      
      // Update the profile state
      setState(prev => {
        // Create updated user with new avatar
        const updatedUser = {
          ...prev.user!,
          avatar: updatedProfile.avatar,
          credits: updatedProfile.credits ?? prev.user?.credits
        };
        
        // Persist the updated user data to localStorage
        const authData = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || '{}');
        authData.user = updatedUser;
        localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authData));
        
        return {
          ...prev,
          user: updatedUser,
          profile: {
            ...prev.profile,
            isComplete: true,
            displayName: updatedProfile.displayName,
            pronouns: updatedProfile.pronouns,
            about: updatedProfile.about,
            bannerColor: updatedProfile.bannerColor,
            bannerUrl: updatedProfile.bannerUrl,
            avatar: updatedProfile.avatar
          },
          isLoading: false
        };
      });
      
    } catch (error) {
      setState(prev => ({ 
        ...prev, 
        isLoading: false, 
        error: error instanceof Error ? error.message : 'Failed to update profile'
      }));
      throw error;
    }
  };

  useEffect(() => {
    initializeAuth();
    return () => {
      if (refreshTimeout) clearTimeout(refreshTimeout);
    };
  }, []);

  const hasRole = useCallback(
    (role: Role): boolean => {
      return !!state.user?.roles?.includes(role);
    },
    [state.user]
  );

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
    hasRole,
    createParty: partyService.createParty,
    getParty: partyService.getParty,
    listParties: partyService.listParties,
    updateParty: partyService.updateParty,
    deleteParty: partyService.deleteParty,
    joinParty: partyService.joinParty,
    updateUserProfile,
  }), [state, login, logout, tokens, startDiscordOAuth, handleDiscordCallback, handleDiscordCallbackWithToken, updateProfile, refreshToken, hasRole, updateUserProfile]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export { AuthProvider }; 