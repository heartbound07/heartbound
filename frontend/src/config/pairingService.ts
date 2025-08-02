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
 * Get all pairing history (admin function) - returns all inactive pairings
 */
export const getAllPairingHistoryForAdmin = async (): Promise<PairingDTO[]> => {
  try {
    const response = await httpClient.get('/pairings/admin/history');
    return response.data;
  } catch (error) {
    console.error('Error fetching admin pairing history:', error);
    return [];
  }
};

/**
 * Get all active pairings in the system
 */
export const getAllActivePairings = async (): Promise<PairingDTO[]> => {
  try {
    const response = await httpClient.get('/pairings/active');
    return response.data;
  } catch (error) {
    console.error('Error fetching all active pairings:', error);
    return [];
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

/**
 * Leaderboard Data Transfer Objects
 */
export interface PairingLeaderboardDTO {
  id: number;
  user1Id: string;
  user2Id: string;
  user1Profile: PublicUserProfileDTO;
  user2Profile: PublicUserProfileDTO;
  discordChannelName: string;
  matchedAt: string;
  messageCount: number;
  user1MessageCount: number;
  user2MessageCount: number;
  voiceTimeMinutes: number;
  wordCount: number;
  emojiCount: number;
  activeDays: number;
  compatibilityScore: number;
  currentLevel: number;
  totalXP: number;
  currentStreak: number;
  active: boolean;
}

export interface PublicUserProfileDTO {
  id: string;
  username: string;
  avatar: string;
  displayName: string;
  pronouns?: string;
  about?: string;
  bannerColor?: string;
  bannerUrl?: string;
  roles: string[];
  badgeUrl?: string;
  badgeName?: string;
  nameplateColor?: string;
  gradientEndColor?: string;
}

/**
 * Get pairing leaderboard with embedded user profiles
 */
export const getPairingLeaderboard = async (): Promise<PairingLeaderboardDTO[]> => {
  try {
    const response = await httpClient.get('/pairings/leaderboard');
    return response.data;
  } catch (error) {
    console.error('Error fetching pairing leaderboard:', error);
    throw error;
  }
}; 