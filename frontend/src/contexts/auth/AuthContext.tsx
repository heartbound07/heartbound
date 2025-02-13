import { createContext } from 'react';
import { AuthContextValue } from './types';

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
  refreshToken: async () => {},
  clearError: () => {},
  startDiscordOAuth: async () => {},
}); 