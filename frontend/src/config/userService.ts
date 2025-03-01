import httpClient from '@/lib/api/httpClient';

export interface UserProfileDTO {
  id: string;
  username: string;
  avatar: string;
}

export const getUserProfile = async (userId: string): Promise<UserProfileDTO> => {
  try {
    const response = await httpClient.get(`/api/users/${userId}/profile`);
    return response.data;
  } catch (error) {
    console.error(`Error fetching user profile for ${userId}:`, error);
    return {
      id: userId,
      username: "Unknown User",
      avatar: "https://v0.dev/placeholder.svg?height=400&width=400"
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
    const response = await httpClient.post('/api/users/profiles', { userIds: validUserIds });
    return response.data;
  } catch (error) {
    console.error('Error fetching user profiles:', error);
    
    // Return fallback profiles
    const fallbackProfiles: Record<string, UserProfileDTO> = {};
    validUserIds.forEach(id => {
      fallbackProfiles[id] = {
        id,
        username: "Unknown User",
        avatar: "https://v0.dev/placeholder.svg?height=400&width=400"
      };
    });
    return fallbackProfiles;
  }
};
