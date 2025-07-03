import httpClient from '@/lib/api/httpClient';
import { Role } from '@/contexts/auth/types';

export interface UserProfileDTO {
  id: string;
  username: string;
  avatar: string;
  displayName?: string;
  pronouns?: string;
  about?: string;
  bannerColor?: string;
  bannerUrl?: string;
  roles?: Role[];
  credits?: number;
  level?: number;
  experience?: number;
  xpForNextLevel?: number;
  messageCount?: number;
  messagesToday?: number;
  messagesThisWeek?: number;
  messagesThisTwoWeeks?: number;
  voiceRank?: number;
  voiceTimeMinutesToday?: number;
  voiceTimeMinutesThisWeek?: number;
  voiceTimeMinutesThisTwoWeeks?: number;
  voiceTimeMinutesTotal?: number;
  equippedUserColorId?: string;
  equippedListingId?: string;
  equippedAccentId?: string;
  equippedBadgeId?: string;
  badgeUrl?: string; // URL of the single equipped badge
  badgeName?: string; // Name of the single equipped badge
  nameplateColor?: string; // Resolved hex color for equipped nameplate
}

export interface UpdateProfileDTO {
  displayName: string;
  pronouns: string;
  about: string;
  bannerColor: string;
  avatar?: string;
  bannerUrl?: string;
}

export const getUserProfile = async (userId: string): Promise<UserProfileDTO> => {
  try {
    const response = await httpClient.get(`/users/${userId}/profile`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching user profile for ${userId}:`, error);
    return {
      id: userId,
      username: "Unknown User",
      avatar: "https://v0.dev/placeholder.svg?height=400&width=400",
      credits: 0
    };
  }
};

// Get multiple user profiles at once
export const getUserProfiles = async (userIds: string[]): Promise<Record<string, UserProfileDTO>> => {
  // Filter out any non-string IDs or empty strings to prevent API errors
  const validUserIds = userIds.filter(id => id && typeof id === 'string');
  
  // If no valid IDs, return empty object immediately
  if (validUserIds.length === 0) {
    return {};
  }
  
  try {
    const response = await httpClient.post('/users/profiles', { userIds: validUserIds });
    return response.data;
  } catch (error) {
    console.error('Error fetching user profiles:', error);
    
    // Return fallback profiles
    const fallbackProfiles: Record<string, UserProfileDTO> = {};
    validUserIds.forEach(id => {
      fallbackProfiles[id] = {
        id,
        username: "Unknown User",
        avatar: "https://v0.dev/placeholder.svg?height=400&width=400",
        credits: 0
      };
    });
    return fallbackProfiles;
  }
};

export const updateUserProfile = async (userId: string, profile: UpdateProfileDTO): Promise<UserProfileDTO> => {
  try {
    const response = await httpClient.put(`/users/${userId}/profile`, profile);
    return response.data;
  } catch (error) {
    console.error(`Error updating user profile for ${userId}:`, error);
    throw error;
  }
};

export const getLeaderboardUsers = async (sortBy: 'credits' | 'level' | 'messages' | 'voice' = 'credits'): Promise<UserProfileDTO[]> => {
  try {
    const response = await httpClient.get('/users/leaderboard', {
      params: { sortBy }
    });
    return response.data;
  } catch (error) {
    console.error('Error fetching leaderboard data:', error);
    return [];
  }
};

export const getCurrentUserProfile = async (): Promise<UserProfileDTO> => {
  try {
    const response = await httpClient.get('/users/me');
    return response.data;
  } catch (error) {
    console.error('Error fetching current user profile:', error);
    throw error;
  }
};

export interface DailyActivityDataDTO {
  date: string; // Format: YYYY-MM-DD
  count: number; // Note: Backend returns Long, we convert to number
}

export interface CombinedDailyActivityDTO {
  date: string; // Format: YYYY-MM-DD
  messages: number;
  voiceMinutes: number;
}

export const getDailyMessageActivity = async (days: number = 30): Promise<DailyActivityDataDTO[]> => {
  try {
    const response = await httpClient.get(`/users/me/activity/daily-messages?days=${days}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching daily message activity:', error);
    throw error;
  }
};

export const getDailyVoiceActivity = async (days: number = 30): Promise<DailyActivityDataDTO[]> => {
  try {
    const response = await httpClient.get(`/users/me/activity/daily-voice?days=${days}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching daily voice activity:', error);
    throw error;
  }
};

export const getCombinedDailyActivity = async (days: number = 30): Promise<CombinedDailyActivityDTO[]> => {
  try {
    // Fetch both message and voice data in parallel
    const [messageData, voiceData] = await Promise.all([
      getDailyMessageActivity(days),
      getDailyVoiceActivity(days)
    ]);
    
    // Create a map of voice data for quick lookup
    const voiceDataMap = new Map(voiceData.map(item => [item.date, Number(item.count)]));
    
    // Combine the data, using message data as the base since it should always exist
    const combinedData: CombinedDailyActivityDTO[] = messageData.map(messageItem => ({
      date: messageItem.date,
      messages: Number(messageItem.count),
      voiceMinutes: voiceDataMap.get(messageItem.date) || 0
    }));
    
    return combinedData;
  } catch (error) {
    console.error('Error fetching combined daily activity:', error);
    throw error;
  }
};
