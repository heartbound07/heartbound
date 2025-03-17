import { createContext } from 'react';
import { AuthContextValue } from './types';

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  profile: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  tokens: null,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
  refreshToken: async () => undefined,
  clearError: () => {},
  startDiscordOAuth: async () => {},
  handleDiscordCallback: async () => {},
  handleDiscordCallbackWithToken: async () => ({ id: '', username: '', email: '', credits: 0 }),
  updateProfile: () => {},
  updateUserProfile: async () => {},
  hasRole: () => false,
  createParty: async () => { throw new Error('Not implemented'); },
  getParty: async () => { throw new Error('Not implemented'); },
  listParties: async () => { throw new Error('Not implemented'); },
  updateParty: async () => { throw new Error('Not implemented'); },
  deleteParty: async () => { throw new Error('Not implemented'); },
  joinParty: async () => { throw new Error('Not implemented'); },
}); 