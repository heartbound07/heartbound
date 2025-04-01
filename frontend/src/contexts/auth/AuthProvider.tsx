import { FC, useEffect, useCallback, useMemo, useRef } from 'react';
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
import { useAuthState } from '../../hooks/useAuthState';
import { useTokenManagement } from '../../hooks/useTokenManagement';

// Update the type declaration
declare global {
  namespace NodeJS {
    interface Timeout {}
  }
}

const AuthProvider: FC<AuthProviderProps> = ({ children }) => {
  // Use our new custom hooks
  const {
    state,
    isUserInfo,
    setAuthState,
    clearAuthState,
    setAuthError,
    setAuthLoading,
    updateAuthProfile,
    hasRole,
  } = useAuthState();

  const {
    tokens,
    parseJwt,
    updateTokens,
    scheduleTokenRefresh,
    refreshTokenRef,
  } = useTokenManagement();

  const persistAuthState = useCallback((user: UserInfo, tokenPair: TokenPair) => {
    // Use the tokenStorage directly instead of a state-updating function
    tokenStorage.setTokens(tokenPair);
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user }));
  }, []);

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
    updateTokens({ accessToken, refreshToken, tokenType, expiresIn, scope });
    setAuthState(user);
  }, [persistAuthState, scheduleTokenRefresh, parseJwt, updateTokens, setAuthState]);

  const login = useCallback(async (credentials: LoginRequest) => {
    setAuthLoading(true);
    try {
      const response = await fetch(AUTH_ENDPOINTS.LOGIN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentials),
      });
      await handleAuthResponse(response);
    } catch (error) {
      clearAuthState();
      setAuthError(error instanceof Error ? error.message : 'Session initialization failed');
      throw error;
    }
  }, [clearAuthState, handleAuthResponse, setAuthError, setAuthLoading]);

  const logout = useCallback(async () => {
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
      clearAuthState();
      updateTokens(null);
    }
  }, [clearAuthState, tokens, updateTokens]);

  // Then ensure refreshToken returns the token
  const refreshToken = useCallback(async () => {
    try {
      // Check if we have a refresh token
      if (!tokens?.refreshToken) {
        console.error("No refresh token available - user needs to re-authenticate");
        // Since we can't refresh without a token, we should logout
        await logout();
        return undefined;
      }
      
      console.log("Refreshing access token...");
      const response = await axios.post(AUTH_ENDPOINTS.REFRESH, { refreshToken: tokens.refreshToken });
      
      let tokenResponse;
      
      if (response.data) {
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
      updateTokens(tokenResponse);
      
      // Get stored user data
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      if (storedAuth) {
        const parsed = JSON.parse(storedAuth);
        if (isUserInfo(parsed.user)) {
          // Persist the updated tokens with the existing user info
          persistAuthState(parsed.user, tokenResponse);
          
          // Calculate new expiration time and schedule refresh
          const newExpiresIn = decodedToken.exp - Math.floor(Date.now() / 1000);
          scheduleTokenRefresh(newExpiresIn);
          
          console.log("Token refresh successful, next refresh scheduled");
          
          // Add this section: Notify WebSocketService to reconnect with the fresh token
          // Small delay ensures localStorage is properly updated first
          setTimeout(() => {
            if (typeof webSocketService !== 'undefined' && 
                webSocketService.reconnectWithFreshToken) {
              webSocketService.reconnectWithFreshToken();
            }
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
  }, [tokens, persistAuthState, scheduleTokenRefresh, logout, updateTokens, parseJwt]);

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
          // Get tokens from memory
          const currentTokens = tokenStorage.getTokens();
          
          if (isUserInfo(parsed.user)) {
            if (currentTokens) {
              // Normal flow - we have both user data and tokens
              setAuthState(parsed.user, parsed.profile || null);
              const decodedToken = parseJwt(currentTokens.accessToken);
              scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));
            } else {
              // We have user data but no tokens (page was refreshed)
              // Set temporary auth state so UI doesn't flash
              setAuthState(parsed.user, parsed.profile || null);
              
              try {
                // Try to refresh the token
                const newToken = await refreshToken();
                if (!newToken) {
                  // If refresh fails, log the user out
                  console.warn("Failed to refresh token on initialization");
                  await logout();
                }
              } catch (error) {
                console.error("Error refreshing token:", error);
                await logout();
              }
            }
          } else {
            // Invalid user data
            await logout();
          }
        }
      }
      setAuthLoading(false);
    } catch (error) {
      console.error('Error initializing auth:', error);
      setAuthState(null);
      setAuthError('Failed to initialize authentication');
    }
  }, [logout, refreshToken, scheduleTokenRefresh, parseJwt, setAuthState, setAuthLoading, setAuthError]);

  const startDiscordOAuth = useCallback(async () => {
    window.location.href = AUTH_ENDPOINTS.DISCORD_AUTHORIZE;
  }, []);

  const handleDiscordCallback = useCallback(async (code: string, state: string) => {
    setAuthLoading(true);
    try {
      const response = await fetch(AUTH_ENDPOINTS.DISCORD_CALLBACK, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code, state }),
      });
      await handleAuthResponse(response);
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Discord authentication failed');
      throw error;
    }
  }, [handleAuthResponse, setAuthError, setAuthLoading]);

  const handleDiscordCallbackWithToken = useCallback(async (accessToken: string, refreshToken?: string) => {
    try {
      setAuthLoading(true);

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
      setAuthState(user, null);
      
      // Setup token refresh timer
      scheduleTokenRefresh(tokenPair.expiresIn - TOKEN_REFRESH_MARGIN / 1000);
      
      // Reconnect websocket with new token
      webSocketService.reconnectWithFreshToken();
      
      return user;
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Authentication failed');
      throw error;
    } finally {
      setAuthLoading(false);
    }
  }, [persistAuthState, scheduleTokenRefresh, parseJwt, setAuthState, setAuthError, setAuthLoading]);

  const updateProfile = useCallback((profile: ProfileStatus) => {
    updateAuthProfile(profile);
  }, [updateAuthProfile]);

  const updateUserProfile = async (profile: UpdateProfileDTO) => {
    if (!state.user?.id) {
      throw new Error('User not authenticated');
    }
    
    try {
      setAuthLoading(true);
      const updatedProfile = await userService.updateUserProfile(state.user.id, profile);
      
      // Create updated user with new avatar
      const updatedUser = {
        ...state.user,
        avatar: updatedProfile.avatar,
        credits: updatedProfile.credits ?? state.user.credits
      };
      
      // Persist the updated user data to localStorage
      const authData = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || '{}');
      authData.user = updatedUser;
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authData));
      
      // Update auth state with the new user info
      setAuthState(updatedUser);
      
      // Update profile separately using updateAuthProfile
      updateAuthProfile({
        ...(state.profile || {}),
        isComplete: true,
        displayName: updatedProfile.displayName,
        pronouns: updatedProfile.pronouns,
        about: updatedProfile.about,
        bannerColor: updatedProfile.bannerColor,
        bannerUrl: updatedProfile.bannerUrl,
        avatar: updatedProfile.avatar
      });
      
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Failed to update profile');
      throw error;
    } finally {
      setAuthLoading(false);
    }
  };

  useEffect(() => {
    initializeAuth();
    // Return a cleanup function
    return () => {
      // Any cleanup code if needed
    };
  }, [initializeAuth]);

  const contextValue = useMemo<AuthContextValue>(() => ({
    ...state,
    login,
    logout,
    register: login,
    refreshToken,
    tokens,
    clearError: () => setAuthError(null),
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