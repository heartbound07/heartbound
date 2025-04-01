import { TokenPair } from './types';

// In-memory token storage (more secure than localStorage)
let inMemoryToken: TokenPair | null = null;

export const tokenStorage = {
  setTokens: (tokens: TokenPair | null) => {
    inMemoryToken = tokens;
    
    // Only store a flag in localStorage indicating auth status
    // (not the actual tokens)
    if (tokens) {
      localStorage.setItem('heartbound_auth_status', 'true');
    } else {
      localStorage.removeItem('heartbound_auth_status');
    }
  },
  
  getTokens: () => {
    return inMemoryToken;
  },
  
  hasStoredAuthStatus: () => {
    return localStorage.getItem('heartbound_auth_status') === 'true';
  },
  
  clearTokens: () => {
    inMemoryToken = null;
    localStorage.removeItem('heartbound_auth_status');
  }
}; 