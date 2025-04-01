import { useState, useCallback } from 'react';
import { AuthState, UserInfo, ProfileStatus } from '../contexts/auth/types';
import { AUTH_STORAGE_KEY } from '../contexts/auth/constants';

export function useAuthState() {
  const [state, setState] = useState<AuthState>({
    user: null,
    profile: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,
  });

  // Type guard for UserInfo validation
  const isUserInfo = (data: unknown): data is UserInfo => {
    return !!data && typeof data === 'object' && 'id' in data && 'username' in data;
  };

  // Update authentication state
  const setAuthState = useCallback((user: UserInfo | null, profile: ProfileStatus | null = null) => {
    setState({
      user,
      profile: profile || state.profile,
      isAuthenticated: !!user,
      isLoading: false,
      error: null,
    });
  }, [state.profile]);

  // Clear authentication state
  const clearAuthState = useCallback(() => {
    setState({
      user: null,
      profile: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
    });
  }, []);

  // Set error state
  const setAuthError = useCallback((errorMessage: string | null) => {
    setState(prev => ({ ...prev, error: errorMessage, isLoading: false }));
  }, []);

  // Set loading state
  const setAuthLoading = useCallback((isLoading: boolean) => {
    setState(prev => ({ ...prev, isLoading }));
  }, []);

  // Update profile
  const updateAuthProfile = useCallback((profile: ProfileStatus) => {
    setState(prev => ({ ...prev, profile }));
  }, []);

  // Check if user has a specific role
  const hasRole = useCallback(
    (role: string): boolean => {
      return !!state.user?.roles?.includes(role as any);
    },
    [state.user]
  );

  return {
    state,
    isUserInfo,
    setAuthState,
    clearAuthState,
    setAuthError,
    setAuthLoading,
    updateAuthProfile,
    hasRole,
  };
}
