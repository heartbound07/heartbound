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
  messageCount?: number;
  messagesToday?: number;
  messagesThisWeek?: number;
  messagesThisTwoWeeks?: number;
  equippedUserColorId?: string;
  equippedListingId?: string;
  equippedAccentId?: string;
  equippedBadgeIds?: string[];
  badgeUrls?: Record<string, string>;
  badgeNames?: Record<string, string>;
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

export const getLeaderboardUsers = async (sortBy: 'credits' | 'level' = 'credits'): Promise<UserProfileDTO[]> => {
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
