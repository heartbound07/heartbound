import httpClient from '../../lib/api/httpClient';
import { PaginatedResponse } from '@/types/api'; // Assuming you have this type defined

export type Rank = 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
export type Region = 'NA_EAST' | 'NA_WEST' | 'NA_CENTRAL' | 'LATAM' | 'BR' | 'EU' | 'KR' | 'AP';

export interface PartyRequirements {
  rank: Rank;
  region: Region;
  inviteOnly: boolean;
}

export interface CreatePartyRequestDTO {
  game: string;
  title: string;
  description: string;
  requirements: PartyRequirements;
  expiresIn: number;
  maxPlayers: number;
  matchType: string;
  gameMode: string;
  teamSize: string;
  voicePreference: string;
  ageRestriction: string;
}

export interface UpdatePartyRequestDTO {
  game?: string;
  title?: string;
  description?: string;
  requirements?: PartyRequirements;
  expiresIn?: number;
  maxPlayers?: number;
  matchType?: string;
  gameMode?: string;
  teamSize?: string;
  voicePreference?: string;
  ageRestriction?: string;
}

export interface LFGPartyResponseDTO {
  id: string;
  userId: string;
  game: string;
  title: string;
  description: string;
  requirements: PartyRequirements;
  expiresIn: number;
  maxPlayers: number;
  status: string;
  createdAt: string;
  expiresAt: string;
  participants: string[];
  matchType: string;
  gameMode: string;
  teamSize: string;
  voicePreference: string;
  ageRestriction: string;
  invitedUsers?: string[];
  joinRequests?: string[];
  discordChannelId?: string;
  discordInviteUrl?: string;
}

export interface ListPartiesParams {
  game?: string;
  title?: string;
  status?: string;
  page?: number;
  size?: number;
}

export const createParty = async (
  data: CreatePartyRequestDTO
): Promise<LFGPartyResponseDTO> => {
  try {
    const response = await httpClient.post('/lfg/parties', data);
    return response.data;
  } catch (error: any) {
    // Propagate a user-friendly error message
    throw new Error(error.response?.data?.message || "Failed to create party");
  }
};

export const getParty = async (id: string): Promise<LFGPartyResponseDTO> => {
  const response = await httpClient.get(`/lfg/parties/${id}`);
  return response.data;
};

export const listParties = async (
  params?: ListPartiesParams
): Promise<PaginatedResponse<LFGPartyResponseDTO>> => {
  const response = await httpClient.get('/lfg/parties', { params });
  return response.data;
};

export const updateParty = async (
  id: string,
  data: UpdatePartyRequestDTO
): Promise<LFGPartyResponseDTO> => {
  const response = await httpClient.put(`/lfg/parties/${id}`, data);
  return response.data;
};

export const deleteParty = async (id: string): Promise<void> => {
  await httpClient.delete(`/lfg/parties/${id}`);
};

export const joinParty = async (id: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${id}/join`);
  return response.data;
};

export const leaveParty = async (id: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${id}/leave`);
  return response.data;
};

export const kickUserFromParty = async (partyId: string, userId: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${partyId}/kick/${userId}`);
  return response.data;
};

export const inviteUserToParty = async (partyId: string, userId: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${partyId}/invite/${userId}`);
  return response.data;
};

export const acceptInvitation = async (partyId: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${partyId}/accept-invite`);
  return response.data;
};

export const getInvitedUsers = async (partyId: string): Promise<string[]> => {
  const response = await httpClient.get(`/lfg/parties/${partyId}/invites`);
  return response.data;
};

export const requestToJoinParty = async (id: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${id}/request-join`);
  return response.data;
};

export const acceptJoinRequest = async (partyId: string, userId: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${partyId}/accept-join-request/${userId}`);
  return response.data;
};

export const rejectJoinRequest = async (partyId: string, userId: string): Promise<string> => {
  const response = await httpClient.post(`/lfg/parties/${partyId}/reject-join-request/${userId}`);
  return response.data;
};
