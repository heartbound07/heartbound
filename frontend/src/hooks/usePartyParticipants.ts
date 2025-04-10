import { useMemo } from 'react';
import { type UserProfileDTO } from "@/config/userService";

interface UsePartyParticipantsParams {
  participants: string[];
  leaderId: string;
  maxPlayers: number;
  userProfiles: Record<string, UserProfileDTO>;
  placeholderAvatar: string;
  currentUser?: { id: string; avatar?: string };
}

export function usePartyParticipants({
  participants,
  leaderId,
  maxPlayers,
  userProfiles,
  placeholderAvatar,
}: UsePartyParticipantsParams) {
  // Calculate joined participants (excluding leader)
  const joinedParticipants = useMemo(() => 
    participants.filter(p => p !== leaderId), 
    [participants, leaderId]
  );
  
  // Calculate empty slots count
  const emptySlotsCount = useMemo(() => 
    maxPlayers - (1 + joinedParticipants.length), 
    [maxPlayers, joinedParticipants.length]
  );

  // Get the leader profile
  const leaderProfile = useMemo(() => 
    userProfiles[leaderId] || {
      avatar: placeholderAvatar,
      username: "Leader",
    }, 
    [userProfiles, leaderId, placeholderAvatar]
  );

  return {
    joinedParticipants,
    emptySlotsCount,
    leaderProfile
  };
} 