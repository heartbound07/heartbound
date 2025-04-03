import { FC, useEffect, useCallback, useMemo, useRef, useState } from 'react';
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

// Place these outside the component to persist across renders
let refreshInProgress = false;
let refreshPromise: Promise<string | undefined> | null = null;

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

  const [initialized, setInitialized] = useState(false);
  const initRan = useRef(false); // Track if init logic ran for this instance

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

  // Update the refreshToken method with mutex pattern
  const refreshToken = async (): Promise<string | undefined> => {
    // If a refresh is already in progress, return the existing promise
    if (refreshInProgress && refreshPromise) {
      console.log('Refresh already in progress, waiting for completion...');
      return refreshPromise;
    }

    console.log('Refreshing token...');

    // Set the mutex and create a new promise
    refreshInProgress = true;
    refreshPromise = (async () => {
      try {
        const refreshTokenValue = tokenStorage.getRefreshToken();
        if (!refreshTokenValue) {
          console.log('No refresh token available - user needs to re-authenticate');
          return undefined;
        }

        const response = await axios.post(AUTH_ENDPOINTS.REFRESH, {
          refreshToken: refreshTokenValue
        });

        // Handle both camelCase and snake_case property names to ensure compatibility
        const accessToken = response.data.access_token || response.data.accessToken;
        const refreshToken = response.data.refresh_token || response.data.refreshToken;
        
        if (accessToken) {
          // Create a properly formed token object with fallbacks for all properties
          const tokenPair = {
            accessToken: accessToken,
            refreshToken: refreshToken || refreshTokenValue, // Keep existing refresh token if not provided
            tokenType: response.data.token_type || response.data.tokenType || 'bearer',
            expiresIn: response.data.expires_in || response.data.expiresIn || 3600,
            scope: response.data.scope || 'identify email'
          };
          
          // Store the new tokens with all required TokenPair properties
          tokenStorage.setTokens(tokenPair);
          
          // Also update the tokens state
          updateTokens(tokenPair);
          
          return accessToken;
        } else {
          console.log('Invalid token response received');
          console.log('Response data:', response.data);
          return undefined;
        }
      } catch (error: any) {
        console.log('Failed to refresh token:', error);
        console.log('Error details:', error.response?.data || error.message);
        
        // Handle specific error cases
        if (error.response?.status === 401) {
          // Clear invalid tokens on 401 to prevent refresh loops
          tokenStorage.setTokens(null);
          clearAuthState();
          throw new Error('Session expired. Please login again.');
        }
        throw error;
      } finally {
        // Release the mutex regardless of success or failure
        refreshInProgress = false;
        refreshPromise = null;
      }
    })();

    return refreshPromise;
  };

  // Set the ref to the function after it's defined
  useEffect(() => {
    refreshTokenRef.current = refreshToken;
  }, [refreshToken]);

  const initializeAuth = useCallback(async () => {
    try {
      // Prevent multiple initializations
      if (initialized) {
        console.log('Auth already initialized, skipping...');
        return;
      }
      
      setAuthLoading(true);
      
      // Get tokens from storage
      const storedTokens = tokenStorage.getTokens();
      
      if (storedTokens?.refreshToken) {
        // We have a refresh token, try to use it
        try {
          // If we have a partial token object (just refresh token), do a token refresh
          if (!storedTokens.accessToken || storedTokens.accessToken === '') {
            console.log('Found refresh token but no access token - attempting to refresh');
            
            // Important: ONLY do this once and await the result
            const newAccessToken = await refreshToken();
            
            // If refresh was successful, we're authenticated
            if (newAccessToken) {
              // Load user data from localStorage
              const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
              if (storedAuth) {
                const parsed = JSON.parse(storedAuth);
                if (isUserInfo(parsed.user)) {
                  // Update auth state with the user info
                  setAuthState(parsed.user, parsed.profile || null);
                  console.log('Successfully restored authentication state after token refresh');
                }
              }
            } else {
              // If refresh failed but didn't throw an error, clean up
              console.log('Token refresh completed but no new access token was received');
              updateTokens(null);
              clearAuthState();
              tokenStorage.setTokens(null);
            }
          } else {
            // We have both tokens, verify the access token
            const decodedToken = parseJwt(storedTokens.accessToken);
            
            // Add null check for decodedToken
            if (decodedToken && decodedToken.exp) {
              const currentTime = Math.floor(Date.now() / 1000);
              
              if (decodedToken.exp > currentTime) {
                // Token is still valid, restore auth state
                const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
                if (storedAuth) {
                  const parsed = JSON.parse(storedAuth);
                  if (isUserInfo(parsed.user)) {
                    setAuthState(parsed.user, parsed.profile || null);
                    
                    // Update tokens state to ensure it's synchronized
                    updateTokens(storedTokens);
                    
                    // Schedule token refresh
                    const timeUntilExpiry = decodedToken.exp - currentTime;
                    scheduleTokenRefresh(timeUntilExpiry);
                    console.log('Access token valid, authentication restored');
                  }
                }
              } else {
                // Token expired, try to refresh
                console.log('Access token expired, attempting refresh');
                await refreshToken();
              }
            } else {
              // Invalid token, try to refresh
              console.log('Invalid access token, attempting refresh');
              await refreshToken();
            }
          }
        } catch (error) {
          console.warn('Failed to refresh token on initialization', error);
          // Clear invalid tokens and wipe storage completely
          updateTokens(null);
          clearAuthState();
          tokenStorage.setTokens(null);
        }
      } else {
        // No tokens found, user is not authenticated
        console.log('No refresh token available - user needs to re-authenticate');
        updateTokens(null);
        clearAuthState();
      }
    } catch (error) {
      console.error('Error initializing auth:', error);
    } finally {
      // Always mark initialization as complete to prevent re-runs
      setInitialized(true);
      setAuthLoading(false);
    }
  }, [clearAuthState, parseJwt, refreshToken, scheduleTokenRefresh, setAuthLoading, setAuthState, updateTokens]);

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

  // Add a debug mount/unmount effect
  useEffect(() => {
    console.log('[DEBUG] AuthProvider mounted');
    return () => console.log('[DEBUG] AuthProvider unmounted');
  }, []);

  // Replace the useEffect that calls initializeAuth with this version
  useEffect(() => {
    // Prevent running twice due to StrictMode or re-renders
    if (initRan.current || initialized) {
      return;
    }
    initRan.current = true;

    const performInit = async () => {
      console.log('Starting auth initialization...');
      setAuthLoading(true); // Ensure loading state is set early
      try {
        await initializeAuth(); // Existing initialization logic
        console.log('Auth initialization completed.');
      } catch (error) {
        console.error('Auth initialization failed:', error);
        // Handle error state appropriately, maybe setAuthError
      } finally {
        setInitialized(true);
        setAuthLoading(false); // Ensure loading state is cleared
      }
    };

    performInit();

    // Cleanup function (optional, if needed)
    return () => {
      console.log('AuthProvider initialization effect cleanup');
    };
    // Keep dependencies that initializeAuth relies on
  }, [initialized, initializeAuth, setAuthLoading]); // Add setAuthLoading

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