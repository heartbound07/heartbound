import { useState, useCallback } from 'react';
import { UserInfo } from '../contexts/auth/types';
import { UserProfileDTO } from '@/config/userService';

// Redefine AuthState here to use UserProfileDTO for user
export interface AuthState {
  user: UserProfileDTO | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export function useAuthState() {
  const [state, setState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,
  });

  // Type guard for UserInfo validation
  const isUserInfo = (data: unknown): data is UserInfo => {
    return !!data && typeof data === 'object' && 'id' in data && 'username' in data;
  };

  // Update authentication state
  const setAuthState = useCallback((user: UserProfileDTO | null) => {
    setState({
      user,
      isAuthenticated: !!user,
      isLoading: false,
      error: null,
    });
  }, []);

  // Clear authentication state
  const clearAuthState = useCallback(() => {
    setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,
    });
  }, []);

  // Set error state
  const setAuthError = useCallback((errorMessage: string | null) => {
    setState(prev => ({ ...prev, error: errorMessage, isLoading: false, user: prev.user }));
  }, []);

  // Set loading state
  const setAuthLoading = useCallback((isLoading: boolean) => {
    setState(prev => ({ ...prev, isLoading }));
  }, []);

  // Update profile
  const updateAuthProfile = useCallback((profile: UserProfileDTO) => {
    setState(prev => ({ ...prev, user: profile }));
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
