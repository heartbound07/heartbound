export const AUTH_STORAGE_KEY = 'heartbound_auth';
export const TOKEN_REFRESH_MARGIN = 300000; // 5 minutes
export const AUTH_ENDPOINTS = {
  LOGIN: '/api/auth/login',
  LOGOUT: '/api/auth/logout',
  REFRESH: '/api/auth/refresh',
  DISCORD_AUTHORIZE: '/api/auth/discord/authorize',
} as const;