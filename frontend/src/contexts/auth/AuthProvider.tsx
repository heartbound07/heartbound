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
import { AUTH_STORAGE_KEY, TOKEN_REFRESH_MARGIN, AUTH_ENDPOINTS, DISCORD_OAUTH_STATE_KEY, AUTH_ERRORS } from './constants';
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

  const persistAuthState = useCallback((user: UserInfo, tokenPair: TokenPair, profile: ProfileStatus | null = null) => {
    // Use the tokenStorage directly instead of a state-updating function
    tokenStorage.setTokens(tokenPair);
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ user, profile }));
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
    persistAuthState(user, { accessToken, refreshToken, tokenType, expiresIn, scope }, null);
    scheduleTokenRefresh(decodedToken.exp - Math.floor(Date.now() / 1000));
    updateTokens({ accessToken, refreshToken, tokenType, expiresIn, scope });
    setAuthState(user);

    // Reconnect WebSocket after successful authentication/token update
    webSocketService.reconnectWithFreshToken();
    console.log('WebSocket reconnect triggered after auth response.');
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
    console.log('[AuthProvider] Starting Discord OAuth flow...');
    setAuthLoading(true); // Keep loading state for UI feedback if needed before navigation
    setAuthError(null);
    try {
      // 1. Generate secure random state
      const state = crypto.randomUUID();
      console.log(`[AuthProvider] Generated state for Discord OAuth: [${state}]`);

      // 2. Store state in localStorage
      localStorage.setItem(DISCORD_OAUTH_STATE_KEY, state);
      console.log(`[AuthProvider] Stored state in localStorage under key: ${DISCORD_OAUTH_STATE_KEY}`);

      // 3. Construct the backend authorization URL with the state
      const authorizeUrl = new URL(AUTH_ENDPOINTS.DISCORD_AUTHORIZE);
      authorizeUrl.searchParams.append('state', state);
      console.log(`[AuthProvider] Constructed backend authorize URL: ${authorizeUrl.toString()}`);

      // 4. Navigate the browser directly to the backend endpoint
      // The backend will handle the redirect to Discord.
      window.location.href = authorizeUrl.toString();

      // Note: Code execution effectively stops here due to navigation.
      // setLoading(false) is not strictly necessary as the page context changes.

    } catch (error) {
      // This catch block might only catch errors during state generation/storage
      // or URL construction, not navigation issues themselves.
      console.error('[AuthProvider] Error preparing for Discord OAuth flow:', error);
      const errorMessage = error instanceof Error ? error.message : 'An unexpected error occurred before redirecting to Discord.';
      setAuthError(`Discord OAuth Error: ${errorMessage}`);
      setAuthLoading(false);
      // Clear potentially invalid state if initiation failed before navigation
      localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
      console.log(`[AuthProvider] Cleared potentially invalid state from localStorage due to error.`);
    }
  }, [setAuthLoading, setAuthError]);

  // New function to exchange the code received on the frontend callback
  const exchangeDiscordCode = useCallback(async (code: string): Promise<void> => {
    try {
      setAuthLoading(true);
      console.log('[Auth] Exchanging Discord code for tokens...');
      
      const response = await fetch(AUTH_ENDPOINTS.DISCORD_EXCHANGE_CODE, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ code }),
      });

      // Extract user data from response first
      const responseData = await response.json();
      
      if (!response.ok) {
        throw new Error(responseData.message || 'Failed to authenticate with Discord');
      }
      
      console.log('[Auth] Discord token exchange successful, processing response data');
      
      // Extract user info directly from the response
      const { accessToken, refreshToken, expiry, user } = responseData;
      
      if (!user || !user.id) {
        throw new Error('Invalid user data received from server');
      }
      
      console.log('[Auth] User data received from server:', user);
      
      // Update tokens first
      updateTokens({
        accessToken,
        refreshToken,
        expiresIn: typeof expiry === 'number' ? expiry : parseInt(expiry, 10),
        tokenType: responseData.tokenType || 'bearer',
        scope: responseData.scope || 'identify email'
      });
      
      // Update auth state with user info
      setAuthState(user);
      
      // Now fetch profile directly using the user ID from the response
      try {
        console.log('[Auth] Fetching profile for user ID:', user.id);
        const profileData = await userService.getUserProfile(user.id);
        console.log('[Auth] Profile data received:', profileData);
        
        if (profileData) {
          // Create profile with required fields and fallbacks
          const profileStatus: ProfileStatus = {
            isComplete: true,
            displayName: profileData.displayName || user.username || '',
            pronouns: profileData.pronouns || '',
            about: profileData.about || '',
            bannerColor: profileData.bannerColor || 'bg-white/10',
            bannerUrl: profileData.bannerUrl || '',
            avatar: profileData.avatar || user.avatar || ''
          };
          
          console.log('[Auth] Created profileStatus:', profileStatus);
          updateAuthProfile(profileStatus);
          
          // Also ensure the profile is persisted to localStorage
          const authStorage = localStorage.getItem(AUTH_STORAGE_KEY);
          if (authStorage) {
            try {
              const authData = JSON.parse(authStorage);
              authData.profile = profileStatus;
              localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authData));
              console.log('[Auth] Updated profile in localStorage');
            } catch (e) {
              console.error('[Auth] Error updating localStorage:', e);
            }
          }
        }
      } catch (profileError) {
        console.error('[Auth] Error fetching user profile after login:', profileError);
        // Create fallback profile from user data
        const fallbackProfile: ProfileStatus = {
          isComplete: false,
          displayName: user.username || '',
          pronouns: '',
          about: '',
          bannerColor: 'bg-white/10',
          bannerUrl: '',
          avatar: user.avatar || ''
        };
        console.log('[Auth] Using fallback profile:', fallbackProfile);
        updateAuthProfile(fallbackProfile);
      }
      
      // Trigger websocket reconnect with new token
      webSocketService.reconnectWithFreshToken();
      
    } catch (error) {
      console.error('[Auth] Error exchanging Discord code:', error);
      clearAuthState();
      updateTokens(null);
      tokenStorage.setTokens(null);
      localStorage.removeItem(AUTH_STORAGE_KEY);
      setAuthError(error instanceof Error ? error.message : 'Failed to authenticate with Discord');
    } finally {
      setAuthLoading(false);
    }
  }, [updateTokens, setAuthState, updateAuthProfile, setAuthLoading, setAuthError, clearAuthState]);

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

  // Add this useEffect to the AuthProvider component
  useEffect(() => {
    // Skip persistence during initial loading
    if (state.isLoading) {
      return;
    }

    // When authenticated and user exists, persist to localStorage
    if (state.isAuthenticated && state.user) {
      const dataToStore = { 
        user: state.user, 
        profile: state.profile 
      };
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(dataToStore));
      console.log('[Auth Persistence] User and profile state persisted');
    } 
    // When not authenticated (and not in loading state), clear localStorage
    else if (!state.isAuthenticated && !state.isLoading) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      console.log('[Auth Persistence] User state cleared from localStorage');
    }
  }, [state.user, state.profile, state.isAuthenticated, state.isLoading]);

  const startRiotOAuth = useCallback(async () => {
    if (!tokens?.accessToken) {
      setAuthError('Authentication required to link Riot account.');
      return;
    }
    setAuthLoading(true);
    try {
      const response = await fetch(AUTH_ENDPOINTS.RIOT_AUTHORIZE, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${tokens.accessToken}`,
        },
      });

      if (!response.ok) {
        // Handle cases where the backend might return an error instead of the URL
        if (response.status === 401) throw new Error(AUTH_ERRORS.UNAUTHORIZED);
        const errorData = await response.json().catch(() => ({ message: 'Failed to initiate Riot OAuth flow.' }));
        throw new Error(errorData.message || AUTH_ERRORS.RIOT_LINK_FAILED);
      }

      // *** Assumes backend returns JSON like { url: "..." } ***
      // If backend returns 302, this needs adjustment.
      const data = await response.json();
      if (!data.url) {
        throw new Error('Invalid response from authorization server.');
      }

      // Redirect the user to the Riot authorization URL
      window.location.href = data.url;
      // No need to set loading false here, as the page will navigate away
    } catch (error) {
      console.error('Riot OAuth initiation error:', error);
      setAuthError(error instanceof Error ? error.message : AUTH_ERRORS.RIOT_LINK_FAILED);
      setAuthLoading(false); // Set loading false only on error
    }
  }, [tokens, setAuthLoading, setAuthError]);

  const unlinkRiotAccount = useCallback(async () => {
    if (!tokens?.accessToken || !state.user) {
      setAuthError('Authentication required to unlink Riot account.');
      return;
    }
    setAuthLoading(true);
    try {
      const response = await fetch(`${AUTH_ENDPOINTS.RIOT_AUTHORIZE}/unlink`, { // Corrected endpoint path
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${tokens.accessToken}`,
        },
      });

      if (!response.ok) {
        if (response.status === 401) throw new Error(AUTH_ERRORS.UNAUTHORIZED);
        const errorData = await response.json().catch(() => ({ message: 'Failed to unlink Riot account.' }));
        throw new Error(errorData.message || 'Failed to unlink Riot account.');
      }

      const data = await response.json();

      // Ensure the response contains the updated user object
      if (!data.user || !isUserInfo(data.user)) {
         throw new Error('Invalid response after unlinking Riot account.');
      }

      const updatedUser: UserInfo = data.user;

      // Update the auth state with the new user info (cleared Riot fields)
      setAuthState(updatedUser);

      // Persist the updated state
      // Retrieve existing profile from localStorage or state if needed
      const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY);
      const existingProfile = storedAuth ? JSON.parse(storedAuth).profile : null;
      persistAuthState(updatedUser, tokens, existingProfile); // Persist updated user

      console.log('Riot account unlinked successfully.');

    } catch (error) {
      console.error('Riot account unlinking error:', error);
      setAuthError(error instanceof Error ? error.message : 'Failed to unlink Riot account.');
    } finally {
      setAuthLoading(false);
    }
  }, [tokens, state.user, setAuthLoading, setAuthError, setAuthState, persistAuthState, isUserInfo]); // Added isUserInfo dependency

  const contextValue = useMemo<AuthContextValue>(() => ({
    ...state,
    login,
    logout,
    register: login,
    refreshToken,
    tokens,
    clearError: () => setAuthError(null),
    startDiscordOAuth,
    exchangeDiscordCode,
    updateProfile,
    hasRole,
    createParty: partyService.createParty,
    getParty: partyService.getParty,
    listParties: partyService.listParties,
    updateParty: partyService.updateParty,
    deleteParty: partyService.deleteParty,
    joinParty: partyService.joinParty,
    updateUserProfile,
    startRiotOAuth,
    unlinkRiotAccount,
  }), [state, login, logout, tokens, startDiscordOAuth, exchangeDiscordCode, updateProfile, refreshToken, hasRole, updateUserProfile, setAuthError, startRiotOAuth, unlinkRiotAccount]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export { AuthProvider }; 