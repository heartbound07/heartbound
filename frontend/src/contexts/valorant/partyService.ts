import httpClient from '../../lib/api/httpClient';

export interface PartyRequirements {
  rank: string;
  region: string;
  voiceChat: boolean;
}

export interface CreatePartyRequestDTO {
  game: string;
  title: string;
  description: string;
  requirements: PartyRequirements;
  expiresIn: number;
  maxPlayers: number;
}

export interface UpdatePartyRequestDTO {
  game?: string;
  title?: string;
  description?: string;
  requirements?: PartyRequirements;
  expiresIn?: number;
  maxPlayers?: number;
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
  const response = await httpClient.post('/api/lfg/parties', data);
  return response.data;
};

export const getParty = async (id: string): Promise<LFGPartyResponseDTO> => {
  const response = await httpClient.get(`/api/lfg/parties/${id}`);
  return response.data;
};

export const listParties = async (
  params?: ListPartiesParams
): Promise<any> => {
  // The params object can include page, size, game, title, and status filters.
  const response = await httpClient.get('/api/lfg/parties', { params });
  return response.data;
};

export const updateParty = async (
  id: string,
  data: UpdatePartyRequestDTO
): Promise<LFGPartyResponseDTO> => {
  const response = await httpClient.put(`/api/lfg/parties/${id}`, data);
  return response.data;
};

export const deleteParty = async (id: string): Promise<void> => {
  await httpClient.delete(`/api/lfg/parties/${id}`);
};

export const joinParty = async (id: string): Promise<string> => {
  const response = await httpClient.post(`/api/lfg/parties/${id}/join`);
  return response.data;
};
