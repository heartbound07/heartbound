export const AUTH_STORAGE_KEY = 'heartbound_auth';
export const TOKEN_REFRESH_MARGIN = 300000; // 5 minutes
export const AUTH_ENDPOINTS = {
  LOGIN: 'http://localhost:8080/api/auth/login',
  REGISTER: 'http://localhost:8080/api/auth/register',
  LOGOUT: 'http://localhost:8080/api/auth/logout',
  REFRESH: 'http://localhost:8080/api/auth/refresh',
  DISCORD_AUTHORIZE: 'http://localhost:8080/api/auth/discord/authorize',
  DISCORD_CALLBACK: 'http://localhost:8080/api/oauth2/callback/discord',
  DISCORD_EXCHANGE_CODE: 'http://localhost:8080/api/auth/discord/exchange-code',
  RIOT_AUTHORIZE: 'http://localhost:8080/api/oauth2/riot/authorize',
} as const;

export const AUTH_ERRORS = {
  INVALID_STATE: 'Invalid authentication state',
  MISSING_CODE: 'Missing authorization code',
  TOKEN_EXPIRED: 'Authentication session expired',
  NETWORK_ERROR: 'Network error occurred',
  UNAUTHORIZED: 'Please log in to access this page',
  RIOT_LINK_FAILED: 'Failed to link Riot account',
} as const;

export const DISCORD_OAUTH_STATE_KEY = 'discordOAuthState';