export interface UserInfo {
  id: string;
  username: string;
  email: string;
  discordId?: string;
  avatar?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthState {
  user: UserInfo | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface AuthContextValue extends AuthState {
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  refreshToken: () => Promise<void>;
  clearError: () => void;
  startDiscordOAuth: () => Promise<void>;
  handleDiscordCallback: (code: string, state: string) => Promise<void>;
}

export interface AuthProviderProps {
  children: React.ReactNode;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface DiscordCallbackParams {
  code: string;
  state: string;
}

export interface DiscordAuthResponse {
  url: string;
  state: string;
} 