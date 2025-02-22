export const AUTH_STORAGE_KEY = 'heartbound_auth';
export const TOKEN_REFRESH_MARGIN = 300000; // 5 minutes
export const AUTH_ENDPOINTS = {
  LOGIN: 'http://localhost:8080/api/auth/login',
  LOGOUT: 'http://localhost:8080/api/auth/logout',
  DISCORD_AUTHORIZE: 'http://localhost:8080/api/auth/discord/authorize',
  DISCORD_CALLBACK: 'http://localhost:8080/api/oauth2/callback/discord',
} as const;

export const AUTH_ERRORS = {
  INVALID_STATE: 'Invalid authentication state',
  MISSING_CODE: 'Missing authorization code',
  TOKEN_EXPIRED: 'Authentication session expired',
  NETWORK_ERROR: 'Network error occurred',
  UNAUTHORIZED: 'Please log in to access this page',
} as const;