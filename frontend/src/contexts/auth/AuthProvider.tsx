import { FC, useEffect, useCallback, useMemo, useRef, useState } from 'react';
import { AuthContext } from './AuthContext';
import {
  AuthContextValue,
  LoginRequest,
  TokenPair,
  UserInfo,
  AuthProviderProps,
} from './types';
import { AUTH_ENDPOINTS, DISCORD_OAUTH_STATE_KEY } from './constants';
import * as partyService from '../valorant/partyService';
import axios from 'axios';
import webSocketService from '../../config/WebSocketService';
import * as userService from '../../config/userService';
import { UpdateProfileDTO, UserProfileDTO } from '@/config/userService';
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
    refreshTokenRef,
  } = useTokenManagement();

  const [initialized, setInitialized] = useState(false);
  const initRan = useRef(false); // Track if init logic ran for this instance

  const persistAuthState = useCallback((tokenPair: TokenPair) => {
    // Use the tokenStorage directly instead of a state-updating function
    tokenStorage.setTokens(tokenPair);
  }, []);

  const handleAuthResponse = useCallback(async (response: Response) => {
    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.message || 'Authentication failed');
    }
    const tokenPair: TokenPair = await response.json();
    updateTokens(tokenPair);

    const userProfileData = await userService.getCurrentUserProfile();
    if (userProfileData) {
      const userInfo: UserInfo = {
        id: userProfileData.id,
        username: userProfileData.username,
        email: '', // Set email to empty string since it doesn't exist on UserProfileDTO
        avatar: userProfileData.avatar,
        roles: userProfileData.roles || [],
        credits: userProfileData.credits || 0,
        level: userProfileData.level || 0,
        experience: userProfileData.experience || 0,
      };
      setAuthState(userInfo, userProfileData);
      persistAuthState(tokenPair);
      
      webSocketService.connect(() => tokenPair.accessToken);
    } else {
      clearAuthState();
      updateTokens(null);
      throw new Error('Failed to fetch user profile after login.');
    }
  }, [updateTokens, parseJwt, setAuthState, persistAuthState, clearAuthState]);

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
      updateTokens(null);
      setAuthError(error instanceof Error ? error.message : 'Session initialization failed');
      throw error;
    }
  }, [clearAuthState, handleAuthResponse, setAuthError, setAuthLoading, updateTokens]);

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
          clearAuthState();
          updateTokens(null);
          return undefined;
        }

        const response = await axios.post(AUTH_ENDPOINTS.REFRESH, {
          refreshToken: refreshTokenValue
        });

        const accessToken = response.data.access_token || response.data.accessToken;
        const newRefreshToken = response.data.refresh_token || response.data.refreshToken;
        
        if (accessToken) {
          const tokenPair: TokenPair = {
            accessToken: accessToken,
            refreshToken: newRefreshToken || refreshTokenValue,
            tokenType: response.data.token_type || response.data.tokenType || 'bearer',
            expiresIn: response.data.expires_in || response.data.expiresIn || 3600,
            scope: response.data.scope || 'identify email'
          };
          
          updateTokens(tokenPair);
          
          // Fetch user profile and update auth state after successful token refresh
          try {
            console.log('[RefreshToken] Successfully refreshed tokens. Fetching user profile...');
            const userProfileData = await userService.getCurrentUserProfile();
            if (userProfileData) {
              const userInfo: UserInfo = {
                id: userProfileData.id,
                username: userProfileData.username,
                email: '', // Consistent with UserProfileDTO not having email
                avatar: userProfileData.avatar,
                roles: userProfileData.roles || [],
                credits: userProfileData.credits || 0,
                level: userProfileData.level || 0,
                experience: userProfileData.experience || 0,
              };
              setAuthState(userInfo, userProfileData);
              webSocketService.reconnectWithFreshToken(); // Ensure WebSocket uses the new token
              console.log('[RefreshToken] User profile fetched and auth state updated after token refresh.');
              return accessToken; // SUCCESS: token refreshed, profile fetched
            } else {
              console.error('[RefreshToken] Critical: Failed to fetch user profile after successful token refresh. No profile data returned.');
              clearAuthState();
              updateTokens(null); // Clear the newly set (but now problematic) tokens
              return undefined; // FAILURE: profile fetch failed
            }
          } catch (profileError: any) {
            console.error('[RefreshToken] Critical: Error fetching user profile after successful token refresh:', profileError);
            clearAuthState();
            updateTokens(null); // Clear the newly set (but now problematic) tokens
            return undefined; // FAILURE: profile fetch errored
          }
        } else {
          console.log('Invalid token response received during refresh');
          console.log('Response data:', response.data);
          // No specific clearAuthState() here as updateTokens(null) might be too aggressive if it was just a malformed response but refresh token is still good.
          // However, if the refresh endpoint itself implies an invalid refresh token (e.g. 401), that's handled below.
          return undefined;
        }
      } catch (error: any) {
        console.log('Failed to refresh token:', error);
        console.log('Error details:', error.response?.data || error.message);
        
        if (error.response?.status === 401) {
          console.log('Clearing tokens and auth state due to 401 during refresh.');
          clearAuthState();
          updateTokens(null);
        }
        return undefined;
      } finally {
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
      const initialTokens = tokenStorage.getTokens();

      if (initialTokens?.refreshToken) {
        let currentTokenPair = initialTokens;
        let currentAccessToken = initialTokens.accessToken;

        const decodedToken = currentAccessToken ? parseJwt(currentAccessToken) : null;
        const currentTime = Math.floor(Date.now() / 1000);

        if (!decodedToken || !decodedToken.exp || decodedToken.exp <= currentTime) {
          if (decodedToken) console.log('[AuthInit] Access token expired or invalid, attempting refresh...');
          else console.log('[AuthInit] No access token, attempting refresh with refresh token...');

          const newAccessTokenValue = await refreshToken(); // This now handles token, profile, authState, websocket
          
          if (!newAccessTokenValue) {
            console.log('[AuthInit] Token refresh process failed during initialization. Auth state should have been cleared by refreshToken.');
            // refreshToken() handles its own cleanup (clearing auth state and tokens) on any failure.
            return; // Exit initialization
          }
          
          // If newAccessTokenValue is truthy, refreshToken has successfully:
          // 1. Refreshed tokens and updated storage.
          // 2. Fetched the user profile.
          // 3. Called setAuthState with user info and profile.
          // 4. Called webSocketService.reconnectWithFreshToken().
          console.log('[AuthInit] Session successfully re-established and user profile updated via token refresh mechanism.');
          // No further profile fetching or setAuthState needed here.

        } else {
          console.log('[AuthInit] Existing access token is valid.');
          updateTokens(currentTokenPair); // Ensure tokens hook state is aligned with the valid tokens from storage

          console.log('[AuthInit] Fetching user profile with existing valid token...');
          const userProfileData = await userService.getCurrentUserProfile(); // Uses the valid currentTokenPair.accessToken via httpClient

          if (userProfileData) {
            const userInfo: UserInfo = {
              id: userProfileData.id,
              username: userProfileData.username,
              email: '', // Consistent with UserProfileDTO not having email
              avatar: userProfileData.avatar,
              roles: userProfileData.roles || [],
              credits: userProfileData.credits || 0,
              level: userProfileData.level || 0,
              experience: userProfileData.experience || 0,
            };
            setAuthState(userInfo, userProfileData);
            console.log('[AuthInit] Authentication state restored, user profile fetched using existing token.');
            // Connect WebSocket with the current valid access token
            webSocketService.connect(() => currentTokenPair.accessToken);
          } else {
            console.error('[AuthInit] Failed to fetch user profile with existing valid token. Clearing auth state.');
            clearAuthState();
            updateTokens(null); // Clear tokens as profile is essential
            setAuthError('Failed to fetch user profile during initialization with existing token.');
            return; // Exit initialization
          }
        }
      } else {
        console.log('[AuthInit] No refresh token found. User is not authenticated.');
        clearAuthState();
        updateTokens(null); 
      }
    } catch (error) {
      console.error('[AuthInit] Initialization process failed:', error);
      clearAuthState();
      updateTokens(null); 
      setAuthError(error instanceof Error ? error.message : 'Failed to initialize session.');
    }
  }, [refreshToken, parseJwt, clearAuthState, updateTokens, setAuthState, setAuthError]); // Dependencies for initializeAuth

  const startDiscordOAuth = useCallback(async () => {
    console.log('[AuthProvider] Starting Discord OAuth flow...');
    setAuthLoading(true); // Keep loading state for UI feedback if needed before navigation
    setAuthError(null);
    try {
      // 1. Generate secure random state
      const stateVal = crypto.randomUUID();
      console.log(`[AuthProvider] Generated state for Discord OAuth: [${stateVal}]`);

      // 2. Store state in localStorage
      localStorage.setItem(DISCORD_OAUTH_STATE_KEY, stateVal);
      console.log(`[AuthProvider] Stored state in localStorage under key: ${DISCORD_OAUTH_STATE_KEY}`);

      // 3. Construct the backend authorization URL with the state
      const authorizeUrl = new URL(AUTH_ENDPOINTS.DISCORD_AUTHORIZE);
      authorizeUrl.searchParams.append('state', stateVal);
      console.log(`[AuthProvider] Constructed backend authorize URL: ${authorizeUrl.toString()}`);

      // 4. Navigate the browser directly to the backend endpoint
      // The backend will handle the redirect to Discord.
      window.location.href = authorizeUrl.toString();

    } catch (error) {
      console.error('[AuthProvider] Error preparing for Discord OAuth flow:', error);
      const errorMessage = error instanceof Error ? error.message : 'An unexpected error occurred before redirecting to Discord.';
      setAuthError(`Discord OAuth Error: ${errorMessage}`);
      setAuthLoading(false);
      localStorage.removeItem(DISCORD_OAUTH_STATE_KEY);
      console.log(`[AuthProvider] Cleared potentially invalid state from localStorage due to error.`);
    }
  }, [setAuthLoading, setAuthError]);

  const exchangeDiscordCode = useCallback(async (code: string): Promise<void> => {
    setAuthLoading(true);
    console.log('[Auth] Exchanging Discord code for tokens...');
    try {
      const response = await fetch(AUTH_ENDPOINTS.DISCORD_EXCHANGE_CODE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
      });

      const responseData = await response.json();
      
      if (!response.ok) {
        throw new Error(responseData.message || 'Failed to authenticate with Discord');
      }
      
      console.log('[Auth] Discord token exchange successful, processing response data');
      
      const { accessToken, refreshToken: newRefreshTokenVal, expiry, user: discordUserData } = responseData;
      
      if (!discordUserData || !discordUserData.id) {
        throw new Error('Invalid user data received from server after Discord auth');
      }
      
      const tokenPair: TokenPair = {
        accessToken,
        refreshToken: newRefreshTokenVal,
        expiresIn: typeof expiry === 'number' ? expiry : parseInt(expiry, 10),
        tokenType: responseData.tokenType || 'bearer',
        scope: responseData.scope || 'identify email'
      };
      updateTokens(tokenPair);
      
      const userProfileData = await userService.getCurrentUserProfile(); 

      if (!userProfileData) {
        throw new Error('Failed to fetch user profile after Discord login.');
      }
      
      const userInfo: UserInfo = {
        id: userProfileData.id,
        username: userProfileData.username,
        email: '', 
        avatar: userProfileData.avatar,
        roles: userProfileData.roles || [],
        credits: userProfileData.credits || 0,
        level: userProfileData.level || 0,
        experience: userProfileData.experience || 0,
      };

      setAuthState(userInfo, userProfileData);
      
      webSocketService.reconnectWithFreshToken();
      
    } catch (error) {
      console.error('[Auth] Error exchanging Discord code:', error);
      clearAuthState();
      updateTokens(null);
      setAuthError(error instanceof Error ? error.message : 'Failed to authenticate with Discord');
    } finally {
      setAuthLoading(false);
    }
  }, [updateTokens, setAuthState, setAuthLoading, setAuthError, clearAuthState]);

  // Fix for updateProfile to handle both ProfileStatus and UserProfileDTO
  const updateProfile = useCallback((newProfileData: UserProfileDTO) => {
    updateAuthProfile(newProfileData);
  }, [updateAuthProfile]);

  const updateUserProfile = async (profileUpdateData: UpdateProfileDTO): Promise<void> => {
    if (!state.user) {
      const err = new Error("User not authenticated to update profile.");
      setAuthError(err.message); // Ensure setAuthError is used
      throw err;
    }
    
    setAuthLoading(true);
    try {
      // This call returns the full updated UserProfileDTO from the backend
      const updatedProfileResponse: UserProfileDTO = await userService.updateUserProfile(state.user.id, profileUpdateData);
      console.log('[AuthProvider] updateUserProfile - Received from backend:', JSON.stringify(updatedProfileResponse));

      const updatedUserInfo: UserInfo = {
        ...state.user, 
        avatar: updatedProfileResponse.avatar, 
        username: updatedProfileResponse.username, 
      };
      console.log('[AuthProvider] updateUserProfile - updatedUserInfo to be set:', JSON.stringify(updatedUserInfo));

      setAuthState(updatedUserInfo, updatedProfileResponse);
      console.log('[AuthProvider] updateUserProfile - Called setAuthState.');
      
      setAuthLoading(false);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to update profile';
      console.error('Error in updateUserProfile:', error);
      setAuthError(errorMessage);
      setAuthLoading(false);
      throw error; // Re-throw error so ProfilePage can catch it for toast notifications
    }
  };

  const fetchCurrentUserProfile = useCallback(async () => {
    if (!tokens?.accessToken) {
      console.warn("[FetchUserProfile] No access token available.");
      return;
    }
    setAuthLoading(true);
    try {
      const userProfileData = await userService.getCurrentUserProfile();
      if (userProfileData) {
        const userInfo: UserInfo = {
          id: userProfileData.id,
          username: userProfileData.username,
          email: '',
          avatar: userProfileData.avatar,
          roles: userProfileData.roles || [],
          credits: userProfileData.credits || 0,
          level: userProfileData.level || 0,
          experience: userProfileData.experience || 0,
        };
        setAuthState(userInfo, userProfileData);
      } else {
        setAuthError("Failed to fetch current user profile: No data returned.");
        clearAuthState();
        updateTokens(null);
      }
    } catch (error: any) {
      console.error('Failed to fetch current user profile:', error);
      setAuthError(error.message || "Failed to fetch profile.");
      if (error.response?.status === 401 || error.response?.status === 403) {
        clearAuthState();
        updateTokens(null);
      }
    } finally {
      setAuthLoading(false);
    }
  }, [tokens?.accessToken, setAuthState, setAuthLoading, setAuthError, clearAuthState, updateTokens]);

  // Add a debug mount/unmount effect
  useEffect(() => {
    console.log('[DEBUG] AuthProvider mounted');
    return () => console.log('[DEBUG] AuthProvider unmounted');
  }, []);

  // Replace the useEffect that calls initializeAuth with this version
  useEffect(() => {
    if (initRan.current || initialized) {
      return;
    }
    initRan.current = true;

    const performInit = async () => {
      console.log('Starting auth initialization...');
      setAuthLoading(true); 
      try {
        await initializeAuth(); 
        console.log('Auth initialization completed.');
      } catch (error) {
        console.error('Auth initialization failed in performInit:', error);
        setAuthError(error instanceof Error ? error.message : 'Critical initialization failure');
        clearAuthState();
        updateTokens(null);
      } finally {
        setInitialized(true);
        setAuthLoading(false); 
      }
    };

    performInit();

    return () => {
      console.log('AuthProvider initialization effect cleanup');
    };
  }, [initialized, initializeAuth, setAuthLoading, setAuthError, clearAuthState, updateTokens]);

  const contextValue = useMemo<AuthContextValue>(() => ({
    ...state,
    login,
    logout,
    register: login,
    refreshToken: refreshTokenRef.current || (async () => undefined),
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
    fetchCurrentUserProfile
  }), [state, login, logout, tokens, startDiscordOAuth, exchangeDiscordCode, updateProfile, refreshTokenRef, hasRole, updateUserProfile, setAuthError, fetchCurrentUserProfile]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export { AuthProvider }; 