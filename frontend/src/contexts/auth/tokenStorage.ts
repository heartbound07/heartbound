import { TokenPair } from './types';

// In-memory token storage (more secure than localStorage)
let inMemoryToken: TokenPair | null = null;

// Key for storing the refresh token in localStorage
const REFRESH_TOKEN_KEY = 'heartbound_refresh_token';

// Key for storing user data in localStorage
const AUTH_STORAGE_KEY = 'heartbound_auth_storage';

export const tokenStorage = {
  setTokens: (tokens: TokenPair | null) => {
    inMemoryToken = tokens;
    
    // Store auth status flag
    if (tokens) {
      localStorage.setItem('heartbound_auth_status', 'true');
      
      // Store only the refresh token in localStorage
      // This is more secure than storing the access token
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    } else {
      localStorage.removeItem('heartbound_auth_status');
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
  },
  
  getTokens: () => {
    // If we have tokens in memory, validate them first
    if (inMemoryToken) {
      // If we have both refresh and access tokens, return the complete object
      if (inMemoryToken.refreshToken && inMemoryToken.accessToken) {
        return inMemoryToken;
      }
      
      // If we have a refresh token but no access token, return a clean partial token object
      // This ensures consistent handling for the refresh flow
      if (inMemoryToken.refreshToken && (!inMemoryToken.accessToken || inMemoryToken.accessToken === '')) {
        return {
          refreshToken: inMemoryToken.refreshToken,
          accessToken: '', // Empty string for consistent handling
          tokenType: 'bearer',
          expiresIn: 0,
          scope: ''
        };
      }
    }
    
    // Check if we have auth status and refresh token
    const hasAuthStatus = localStorage.getItem('heartbound_auth_status') === 'true';
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    
    // If we have a refresh token but no in-memory token, return a partial token object
    // The access token will be refreshed by the auth flow
    if (hasAuthStatus && refreshToken) {
      return {
        refreshToken,
        accessToken: '', // Empty access token will trigger a refresh
        tokenType: 'bearer',
        expiresIn: 0,
        scope: ''
      };
    }
    
    return null;
  },
  
  hasStoredAuthStatus: () => {
    return localStorage.getItem('heartbound_auth_status') === 'true';
  },
  
  getRefreshToken: () => {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  
  clearTokens: () => {
    // Clear in-memory token
    inMemoryToken = null;
    
    // Clear all auth-related items from localStorage
    localStorage.removeItem('heartbound_auth_status');
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    
    // Also clear any user data stored in AUTH_STORAGE_KEY
    localStorage.removeItem(AUTH_STORAGE_KEY);
    
    // Clear any additional auth flags that might be present
    localStorage.removeItem('heartbound_user');
    localStorage.removeItem('heartbound_profile');
    
    console.log('All authentication tokens and data cleared');
  }
}; 