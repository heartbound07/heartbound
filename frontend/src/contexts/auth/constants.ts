export const AUTH_STORAGE_KEY = 'heartbound_auth';
export const TOKEN_REFRESH_MARGIN = 300000; // 5 minutes
export const AUTH_ENDPOINTS = {
  LOGIN: '/api/auth/login',
  LOGOUT: '/api/auth/logout',
  REFRESH: '/api/auth/refresh',
  DISCORD_AUTHORIZE: '/api/auth/discord/authorize',
  DISCORD_CALLBACK: '/api/oauth2/callback/discord',
} as const;

export const AUTH_ERRORS = {
  INVALID_STATE: 'Invalid authentication state',
  MISSING_CODE: 'Missing authorization code',
  TOKEN_EXPIRED: 'Authentication session expired',
  NETWORK_ERROR: 'Network error occurred',
  UNAUTHORIZED: 'Please log in to access this page',
} as const;