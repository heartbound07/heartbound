export const AUTH_STORAGE_KEY = 'heartbound_auth';
export const TOKEN_REFRESH_MARGIN = 300000; // 5 minutes

// Get API URL from environment or fallback to localhost if not defined
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const AUTH_ENDPOINTS = {
  LOGIN: `${API_URL}/auth/login`,
  REGISTER: `${API_URL}/auth/register`,
  LOGOUT: `${API_URL}/auth/logout`,
  REFRESH: `${API_URL}/auth/refresh`,
  DISCORD_AUTHORIZE: `${API_URL}/auth/discord/authorize`,
  DISCORD_CALLBACK: `${API_URL}/oauth2/callback/discord`,
  DISCORD_EXCHANGE_CODE: `${API_URL}/auth/discord/exchange-code`,
} as const;

export const AUTH_ERRORS = {
  INVALID_STATE: 'Invalid authentication state',
  MISSING_CODE: 'Missing authorization code',
  TOKEN_EXPIRED: 'Authentication session expired',
  NETWORK_ERROR: 'Network error occurred',
  UNAUTHORIZED: 'Please log in to access this page',
} as const;

export const DISCORD_OAUTH_STATE_KEY = 'discordOAuthState';