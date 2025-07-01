const PUBLIC_PATHS = ['/', '/login', '/riot.txt'];
const AUTH_PATHS = ['/auth', '/api/auth'];
const PROTECTED_PATHS = ['/profile', '/admin', '/leaderboard', '/settings', '/shop', '/inventory', '/pairings'];

export const isPublicPath = (pathname: string): boolean => {
  return PUBLIC_PATHS.some(path => pathname.startsWith(path));
};

export const isAuthPath = (pathname: string): boolean => {
  return AUTH_PATHS.some(path => pathname.startsWith(path));
};

export const isProtectedPath = (pathname: string): boolean => {
  return PROTECTED_PATHS.some(path => pathname.startsWith(path));
};

export const requiresAuth = (pathname: string): boolean => {
  return isProtectedPath(pathname) && !isPublicPath(pathname) && !isAuthPath(pathname);
}; 