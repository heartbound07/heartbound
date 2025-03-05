import { CreatePartyRequestDTO, LFGPartyResponseDTO, ListPartiesParams, UpdatePartyRequestDTO } from '../valorant/partyService';
import { UpdateProfileDTO } from '@/config/userService';

export type Role = 'USER' | 'MONARCH' | 'MODERATOR' | 'ADMIN';

export interface UserInfo {
  id: string;
  username: string;
  email: string;
  discordId?: string;
  avatar?: string;
  roles?: Role[];
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

export interface AuthState {
  user: UserInfo | null;
  profile: ProfileStatus | null;
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
  handleDiscordCallback: (code: string, state: string) => Promise<void>;
  handleDiscordCallbackWithToken: (accessToken: string, refreshToken?: string) => Promise<UserInfo>;
  updateProfile: (profile: ProfileStatus) => void;
  updateUserProfile: (profile: UpdateProfileDTO) => Promise<void>;
  hasRole: (role: Role) => boolean;
  
  // Party service functions
  createParty: (data: CreatePartyRequestDTO) => Promise<LFGPartyResponseDTO>;
  getParty: (id: string) => Promise<LFGPartyResponseDTO>;
  listParties: (params?: ListPartiesParams) => Promise<any>;
  updateParty: (id: string, data: UpdatePartyRequestDTO) => Promise<LFGPartyResponseDTO>;
  deleteParty: (id: string) => Promise<void>;
  joinParty: (id: string) => Promise<string>;
}

export interface AuthProviderProps {
  children: React.ReactNode;
}

export interface DiscordCallbackParams {
  code: string;
  state: string;
}

export interface DiscordAuthResponse {
  url: string;
  state: string;
} 