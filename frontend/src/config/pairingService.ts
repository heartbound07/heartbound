import httpClient from '@/lib/api/httpClient';

export interface PairingDTO {
  id: number;
  user1Id: string;
  user2Id: string;
  discordChannelId: number;
  matchedAt: string;
  messageCount: number;
  wordCount: number;
  emojiCount: number;
  activeDays: number;
  compatibilityScore: number;
  breakupInitiatorId?: string;
  breakupReason?: string;
  breakupTimestamp?: string;
  mutualBreakup: boolean;
  active: boolean;
  blacklisted: boolean;
}

export interface MatchQueueUserDTO {
  userId: string;
  age: number;
  region: 'NA_EAST' | 'NA_WEST' | 'EU' | 'ASIA' | 'OCE';
  rank: 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
  queuedAt: string;
  inQueue: boolean;
}

export interface JoinQueueRequestDTO {
  userId: string;
  age: number;
  region: string;
  rank: string;
}

export interface QueueStatusDTO {
  inQueue: boolean;
  queuedAt?: string;
  estimatedWaitTime?: number;
}

/**
 * Get the current user's active pairing
 */
export const getCurrentUserPairing = async (userId: string): Promise<PairingDTO | null> => {
  try {
    const response = await httpClient.get(`/pairings/current?userId=${userId}`);
    return response.data;
  } catch (error: any) {
    if (error.response?.status === 404) {
      return null; // No active pairing found
    }
    console.error('Error fetching current pairing:', error);
    throw error;
  }
};

/**
 * Get pairing history for the current user
 */
export const getPairingHistory = async (userId: string): Promise<PairingDTO[]> => {
  try {
    const response = await httpClient.get(`/pairings/history?userId=${userId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching pairing history:', error);
    return [];
  }
};

/**
 * Join the matchmaking queue
 * Note: This assumes a backend endpoint exists. If not, this would need to be created.
 */
export const joinMatchmakingQueue = async (preferences: JoinQueueRequestDTO): Promise<void> => {
  try {
    await httpClient.post('/matchmaking/join', preferences);
  } catch (error) {
    console.error('Error joining matchmaking queue:', error);
    throw error;
  }
};

/**
 * Leave the matchmaking queue
 */
export const leaveMatchmakingQueue = async (): Promise<void> => {
  try {
    await httpClient.post('/matchmaking/leave');
  } catch (error) {
    console.error('Error leaving matchmaking queue:', error);
    throw error;
  }
};

/**
 * Get current queue status for user
 */
export const getQueueStatus = async (userId: string): Promise<QueueStatusDTO> => {
  try {
    const response = await httpClient.get(`/matchmaking/status?userId=${userId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching queue status:', error);
    return { inQueue: false };
  }
};

/**
 * Trigger manual matchmaking (admin function)
 */
export const performMatchmaking = async (): Promise<PairingDTO[]> => {
  try {
    const response = await httpClient.post('/pairings/matchmake');
    return response.data;
  } catch (error) {
    console.error('Error performing matchmaking:', error);
    throw error;
  }
}; 