import httpClient from '@/lib/api/httpClient';

export interface PairingDTO {
  id: number;
  user1Id: string;
  user2Id: string;
  discordChannelId: number;
  discordChannelName?: string;
  matchedAt: string;
  messageCount: number;
  user1MessageCount: number;
  user2MessageCount: number;
  voiceTimeMinutes: number;
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
  user1Age?: number;
  user1Gender?: string;
  user1Region?: string;
  user1Rank?: string;
  user2Age?: number;
  user2Gender?: string;
  user2Region?: string;
  user2Rank?: string;
}

export interface MatchQueueUserDTO {
  userId: string;
  age: number;
  region: 'NA_EAST' | 'NA_WEST' | 'EU' | 'ASIA' | 'OCE';
  rank: 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
  gender: 'MALE' | 'FEMALE' | 'NON_BINARY' | 'PREFER_NOT_TO_SAY';
  queuedAt: string;
  inQueue: boolean;
}

export interface QueueStatusDTO {
  inQueue: boolean;
  queuedAt?: string;
  estimatedWaitTime?: number;
  queuePosition?: number;
  totalQueueSize?: number;
  age?: number;
  region?: 'NA_EAST' | 'NA_WEST' | 'EU' | 'ASIA' | 'OCE';
  rank?: 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
  gender?: 'MALE' | 'FEMALE' | 'NON_BINARY' | 'PREFER_NOT_TO_SAY';
}

export interface JoinQueueRequestDTO {
  userId: string;
  age: number;
  region: 'NA_EAST' | 'NA_WEST' | 'EU' | 'ASIA' | 'OCE';
  rank: 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
  gender: 'MALE' | 'FEMALE' | 'NON_BINARY' | 'PREFER_NOT_TO_SAY';
}

export interface QueueConfigDTO {
  queueEnabled: boolean;
  message: string;
  updatedBy: string;
  timestamp: string;
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
export const joinMatchmakingQueue = async (preferences: JoinQueueRequestDTO): Promise<QueueStatusDTO> => {
  try {
    const response = await httpClient.post('/matchmaking/join', preferences);
    return response.data;
  } catch (error) {
    console.error('Error joining matchmaking queue:', error);
    throw error;
  }
};

/**
 * Leave the matchmaking queue
 */
export const leaveMatchmakingQueue = async (userId: string): Promise<void> => {
  try {
    await httpClient.post('/matchmaking/leave', null, {
      params: { userId }
    });
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
  console.log('Frontend: Calling performMatchmaking endpoint');
  try {
    const response = await httpClient.post('/pairings/matchmake');
    console.log('Frontend: Matchmaking response received', response.data);
    return response.data;
  } catch (error) {
    console.error('Frontend: Matchmaking call failed', error);
    throw error;
  }
};

/**
 * Delete all active pairings (admin function)
 */
export const deleteAllPairings = async (): Promise<{ message: string; deletedCount: number }> => {
  try {
    const response = await httpClient.delete('/pairings/admin/all');
    return response.data;
  } catch (error) {
    console.error('Error deleting all pairings:', error);
    throw error;
  }
};

/**
 * Admin unpair users - ends active pairing but keeps blacklist (admin function)
 */
export const unpairUsers = async (pairingId: number): Promise<void> => {
  try {
    await httpClient.post(`/pairings/admin/${pairingId}/unpair`);
  } catch (error) {
    console.error('Error unpairing users:', error);
    throw error;
  }
};

/**
 * Permanently delete a specific pairing record (admin function)
 */
export const deletePairingById = async (pairingId: number): Promise<void> => {
  try {
    await httpClient.delete(`/pairings/admin/history/${pairingId}`);
  } catch (error) {
    console.error('Error deleting pairing:', error);
    throw error;
  }
};

/**
 * Permanently delete all inactive pairings (admin function)
 */
export const clearInactivePairingHistory = async (): Promise<{ message: string; deletedCount: number }> => {
  try {
    const response = await httpClient.delete('/pairings/admin/history/all-inactive');
    return response.data;
  } catch (error) {
    console.error('Error clearing inactive pairing history:', error);
    throw error;
  }
};

// Add admin queue control functions
export const enableQueue = async (): Promise<QueueConfigDTO> => {
  const response = await httpClient.post('/pairings/admin/queue/enable');
  return response.data;
};

export const disableQueue = async (): Promise<QueueConfigDTO> => {
  const response = await httpClient.post('/pairings/admin/queue/disable');
  return response.data;
};

export const getQueueConfig = async (): Promise<QueueConfigDTO> => {
  const response = await httpClient.get('/pairings/admin/queue/config');
  return response.data;
};

export const getPublicQueueStatus = async (): Promise<QueueConfigDTO> => {
  const response = await httpClient.get('/pairings/queue/status');
  return response.data;
};

/**
 * Initiate a breakup for a pairing
 */
export const breakupPairing = async (
  pairingId: number, 
  initiatorId: string, 
  reason: string
): Promise<PairingDTO> => {
  try {
    const response = await httpClient.post(`/pairings/${pairingId}/breakup`, {
      initiatorId,
      reason,
      mutualBreakup: false
    });
    return response.data;
  } catch (error) {
    console.error('Error initiating breakup:', error);
    throw error;
  }
}; 