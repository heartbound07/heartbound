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

export interface QueueStatsDTO {
  totalUsersInQueue: number;
  averageWaitTimeMinutes: number;
  lastMatchmakingRun: string;
  queueByRegion: Record<string, number>;
  queueByRank: Record<string, number>;
  queueByGender: Record<string, number>;
  queueByAgeRange: Record<string, number>;
  matchSuccessRate: number;
  totalMatchesCreatedToday: number;
  totalUsersMatchedToday: number;
  queueSizeHistory: Record<string, number>;
  waitTimeHistory: Record<string, number>;
  queueStartTime: string;
  queueEnabled: boolean;
  lastUpdatedBy: string;
}

export interface QueueUserDetailsDTO {
  userId: string;
  username: string;
  avatar: string;
  age: number;
  region: 'NA_EAST' | 'NA_WEST' | 'EU' | 'LATAM' | 'BR' | 'KR' | 'AP';
  rank: 'IRON' | 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'DIAMOND' | 'ASCENDANT' | 'IMMORTAL' | 'RADIANT';
  gender: 'MALE' | 'FEMALE' | 'NON_BINARY' | 'PREFER_NOT_TO_SAY';
  queuedAt: string;
  waitTimeMinutes: number;
  queuePosition: number;
  estimatedWaitTimeMinutes: number;
  recentlyQueued: boolean;
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

/**
 * Get queue statistics (admin function)
 */
export const getQueueStatistics = async (): Promise<QueueStatsDTO> => {
  try {
    const response = await httpClient.get('/pairings/admin/queue/statistics');
    return response.data;
  } catch (error) {
    console.error('Error fetching queue statistics:', error);
    throw error;
  }
};

/**
 * Trigger manual refresh of queue statistics (admin function)
 */
export const refreshQueueStatistics = async (): Promise<QueueStatsDTO> => {
  try {
    const response = await httpClient.post('/pairings/admin/queue/statistics/refresh');
    return response.data;
  } catch (error) {
    console.error('Error refreshing queue statistics:', error);
    throw error;
  }
};

/**
 * Warm up queue statistics cache (admin function)
 */
export const warmUpQueueStatsCache = async (): Promise<{ status: string; message: string }> => {
  try {
    const response = await httpClient.post('/pairings/admin/queue/cache/warmup');
    return response.data;
  } catch (error) {
    console.error('Error warming up cache:', error);
    throw error;
  }
};

/**
 * Get cache status (admin function)
 */
export const getCacheStatus = async (): Promise<Record<string, any>> => {
  try {
    const response = await httpClient.get('/pairings/admin/queue/cache/status');
    return response.data;
  } catch (error) {
    console.error('Error fetching cache status:', error);
    throw error;
  }
};

/**
 * Get detailed queue user information (admin function)
 */
export const getQueueUserDetails = async (): Promise<QueueUserDetailsDTO[]> => {
  try {
    const response = await httpClient.get('/pairings/admin/queue/users');
    return response.data;
  } catch (error) {
    console.error('Error fetching queue user details:', error);
    throw error;
  }
};

// XP System Types
export interface PairLevelDTO {
  id: number;
  pairingId: number;
  currentLevel: number;
  totalXP: number;
  currentLevelXP: number;
  nextLevelXP: number;
  xpNeededForNextLevel: number;
  levelProgressPercentage: number;
  readyToLevelUp: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AchievementDTO {
  id: number;
  achievementKey: string;
  name: string;
  description: string;
  achievementType: string;
  xpReward: number;
  requirementValue: number;
  requirementDescription: string;
  iconUrl?: string;
  badgeColor?: string;
  rarity: string;
  tier: number;
  active: boolean;
  hidden: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PairAchievementDTO {
  id: number;
  pairingId: number;
  achievement: AchievementDTO;
  unlockedAt: string;
  progressValue: number;
  xpAwarded: number;
  notified: boolean;
  recentlyUnlocked: boolean;
  unlockTimeDisplay: string;
  createdAt: string;
}

export interface VoiceStreakDTO {
  id: number;
  pairingId: number;
  streakDate: string;
  voiceMinutes: number;
  streakCount: number;
  active: boolean;
  isToday: boolean;
  isYesterday: boolean;
  meetsMinimumActivity: boolean;
  streakXPReward: number;
  streakTier: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdatePairingActivityDTO {
  messageIncrement: number;
  wordIncrement: number;
  emojiIncrement: number;
  activeDays?: number;
  // Admin-only fields for direct metric updates
  user1MessageCount?: number;
  user2MessageCount?: number;
  voiceTimeMinutes?: number;
}

// XP System API Functions

/**
 * Get pair level and XP information
 */
export const getPairLevel = async (pairingId: number): Promise<PairLevelDTO> => {
  try {
    const response = await httpClient.get(`/pairings/${pairingId}/level`);
    return response.data;
  } catch (error) {
    console.error('Error fetching pair level:', error);
    throw error;
  }
};

/**
 * Get achievements for a pairing
 */
export const getPairingAchievements = async (pairingId: number): Promise<PairAchievementDTO[]> => {
  try {
    const response = await httpClient.get(`/pairings/${pairingId}/achievements`);
    return response.data;
  } catch (error) {
    console.error('Error fetching pairing achievements:', error);
    throw error;
  }
};

/**
 * Get available achievements for a pairing
 */
export const getAvailableAchievements = async (pairingId: number): Promise<AchievementDTO[]> => {
  try {
    const response = await httpClient.get(`/pairings/${pairingId}/achievements/available`);
    return response.data;
  } catch (error) {
    console.error('Error fetching available achievements:', error);
    throw error;
  }
};

/**
 * Get voice streaks for a pairing
 */
export const getVoiceStreaks = async (pairingId: number): Promise<{ statistics: any; recentStreaks: VoiceStreakDTO[] }> => {
  try {
    const response = await httpClient.get(`/pairings/${pairingId}/streaks`);
    return response.data;
  } catch (error) {
    console.error('Error fetching voice streaks:', error);
    throw error;
  }
};

/**
 * Batch fetch level data for multiple pairings
 */
export const getBatchPairLevels = async (pairingIds: number[]): Promise<Record<number, PairLevelDTO>> => {
  try {
    const promises = pairingIds.map(async (id) => {
      try {
        const response = await httpClient.get(`/pairings/${id}/level`);
        return { id, data: response.data };
      } catch (error) {
        console.warn(`Failed to fetch level for pairing ${id}:`, error);
        return { id, data: null };
      }
    });

    const results = await Promise.allSettled(promises);
    const levelData: Record<number, PairLevelDTO> = {};

    results.forEach((result) => {
      if (result.status === 'fulfilled' && result.value.data) {
        levelData[result.value.id] = result.value.data;
      }
    });

    return levelData;
  } catch (error) {
    console.error('Error batch fetching pair levels:', error);
    return {};
  }
};

/**
 * Batch fetch current streak counts for multiple pairings
 */
export const getBatchCurrentStreaks = async (pairingIds: number[]): Promise<Record<number, number>> => {
  try {
    const promises = pairingIds.map(async (id) => {
      try {
        const response = await httpClient.get(`/pairings/${id}/streaks`);
        return { id, currentStreak: response.data.statistics.currentStreak || 0 };
      } catch (error) {
        console.warn(`Failed to fetch streaks for pairing ${id}:`, error);
        return { id, currentStreak: 0 };
      }
    });

    const results = await Promise.allSettled(promises);
    const streakData: Record<number, number> = {};

    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        streakData[result.value.id] = result.value.currentStreak;
      }
    });

    return streakData;
  } catch (error) {
    console.error('Error batch fetching current streaks:', error);
    return {};
  }
};

/**
 * Manually trigger achievement checking (admin only)
 */
export const checkAchievements = async (pairingId: number): Promise<PairAchievementDTO[]> => {
  try {
    const response = await httpClient.post(`/pairings/achievements/check/${pairingId}`);
    return response.data;
  } catch (error) {
    console.error('Error checking achievements:', error);
    throw error;
  }
};

/**
 * Update pairing activity metrics
 */
export const updatePairingActivity = async (pairingId: number, activity: UpdatePairingActivityDTO): Promise<PairingDTO> => {
  try {
    const response = await httpClient.patch(`/pairings/${pairingId}/activity`, activity);
    return response.data;
  } catch (error) {
    console.error('Error updating pairing activity:', error);
    throw error;
  }
};

// Admin-only XP/Level Management
export interface UpdatePairLevelDTO {
  currentLevel?: number;
  totalXP?: number;
  xpIncrement?: number; // Add this much XP (can be negative)
}

/**
 * Admin: Directly update pair level and XP (admin only)
 */
export const updatePairLevel = async (pairingId: number, levelUpdate: UpdatePairLevelDTO): Promise<PairLevelDTO> => {
  try {
    const response = await httpClient.patch(`/pairings/${pairingId}/level/admin`, levelUpdate);
    return response.data;
  } catch (error) {
    console.error('Error updating pair level:', error);
    throw error;
  }
};

// Admin-only Achievement Management
export interface ManageAchievementDTO {
  achievementId: number;
  action: 'unlock' | 'lock';
  customXP?: number; // Override default XP if unlocking
}

/**
 * Admin: Manually unlock or lock an achievement (admin only)
 */
export const manageAchievement = async (pairingId: number, achievementAction: ManageAchievementDTO): Promise<PairAchievementDTO | { message: string }> => {
  try {
    const response = await httpClient.post(`/pairings/${pairingId}/achievements/admin/manage`, achievementAction);
    return response.data;
  } catch (error) {
    console.error('Error managing achievement:', error);
    throw error;
  }
};

// Admin-only Voice Streak Management
export interface UpdateVoiceStreakDTO {
  streakDate: string; // ISO date string
  voiceMinutes: number;
  streakCount?: number; // Override calculated streak count
  active?: boolean;
}

export interface CreateVoiceStreakDTO {
  streakDate: string;
  voiceMinutes: number;
  streakCount: number;
  active: boolean;
}

/**
 * Admin: Update an existing voice streak (admin only)
 */
export const updateVoiceStreak = async (streakId: number, streakUpdate: UpdateVoiceStreakDTO): Promise<VoiceStreakDTO> => {
  try {
    const response = await httpClient.patch(`/pairings/voice-streaks/${streakId}/admin`, streakUpdate);
    return response.data;
  } catch (error) {
    console.error('Error updating voice streak:', error);
    throw error;
  }
};

/**
 * Admin: Create a new voice streak (admin only)
 */
export const createVoiceStreak = async (pairingId: number, streakData: CreateVoiceStreakDTO): Promise<VoiceStreakDTO> => {
  try {
    const response = await httpClient.post(`/pairings/${pairingId}/streaks/admin`, streakData);
    return response.data;
  } catch (error) {
    console.error('Error creating voice streak:', error);
    throw error;
  }
};

/**
 * Admin: Delete a voice streak (admin only)
 */
export const deleteVoiceStreak = async (streakId: number): Promise<{ message: string }> => {
  try {
    const response = await httpClient.delete(`/pairings/voice-streaks/${streakId}/admin`);
    return response.data;
  } catch (error) {
    console.error('Error deleting voice streak:', error);
    throw error;
  }
}; 