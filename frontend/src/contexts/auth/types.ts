import { CreatePartyRequestDTO, LFGPartyResponseDTO, ListPartiesParams, UpdatePartyRequestDTO } from '../valorant/partyService';
import { UpdateProfileDTO, UserProfileDTO } from '@/config/userService';

export type Role = 'USER' | 'MONARCH' | 'MODERATOR' | 'ADMIN';

export interface UserInfo {
  id: string;
  username: string;
  discordId?: string;
  avatar?: string;
  roles?: Role[];
  credits?: number;
  level?: number;
  experience?: number;
}

export interface ProfileStatus {
  isComplete: boolean;
  requiredFields?: string[];
  displayName?: string;
  pronouns?: string;
  about?: string;
  bannerColor?: string;
  bannerUrl?: string;
  avatar?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface AuthState {
  user: UserInfo | null;
  profile: UserProfileDTO | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  scope: string;
}

export interface AuthContextValue extends AuthState {
  tokens: TokenPair | null;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  refreshToken: () => Promise<string | undefined>;
  clearError: () => void;
  startDiscordOAuth: () => Promise<void>;
  exchangeDiscordCode: (code: string) => Promise<void>;
  updateProfile: (profile: UserProfileDTO) => void;
  updateUserProfile: (profile: UpdateProfileDTO) => Promise<void>;
  hasRole: (role: Role) => boolean;
  
  // Party service functions
  createParty: (data: CreatePartyRequestDTO) => Promise<LFGPartyResponseDTO>;
  getParty: (id: string) => Promise<LFGPartyResponseDTO>;
  listParties: (params?: ListPartiesParams) => Promise<any>;
  updateParty: (id: string, data: UpdatePartyRequestDTO) => Promise<LFGPartyResponseDTO>;
  deleteParty: (id: string) => Promise<void>;
  joinParty: (id: string) => Promise<string>;
  fetchCurrentUserProfile: () => Promise<void>;
}

export interface AuthProviderProps {
  children: React.ReactNode;
}

export interface DiscordAuthResponse {
  url: string;
  state: string;
} 