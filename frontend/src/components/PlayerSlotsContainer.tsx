"use client"

import * as React from "react"
import { Users, Crown, Plus } from "lucide-react"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { type UserProfileDTO } from "@/config/userService"
import { usePartyParticipants } from "@/hooks/usePartyParticipants"

// Individual PlayerSlot component to reduce duplication
interface PlayerSlotProps {
  avatarUrl: string;
  username: string;
  tooltipText?: string;
  isLeader?: boolean;
  isEmpty?: boolean;
  onClick?: () => void;
  currentUserId?: string;
  participantId?: string;
}

const PlayerSlot: React.FC<PlayerSlotProps> = ({
  avatarUrl,
  username,
  tooltipText,
  isLeader = false,
  isEmpty = false,
  onClick,
  currentUserId,
  participantId
}) => {
  // Determine if this slot represents the current user
  const isCurrentUser = currentUserId && participantId && currentUserId === participantId;
  
  // Select appropriate styles based on slot type
  const gradientColors = isLeader 
    ? "from-purple-600 to-blue-600" 
    : isEmpty 
      ? "" 
      : isCurrentUser 
        ? "from-yellow-500 to-orange-500"
        : "from-green-500 to-blue-500";

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div 
          className={`relative group ${!isEmpty ? "cursor-default" : "cursor-pointer"}`}
          onClick={isEmpty ? onClick : undefined}
        >
          {!isEmpty && (
            <div className={`absolute -inset-0.5 bg-gradient-to-r ${gradientColors} rounded-full opacity-75 group-hover:opacity-100 transition duration-300 blur`} />
          )}
          
          <div className={`relative w-full aspect-square rounded-full ${
            isEmpty 
              ? "border-2 border-purple-500/20 p-1 bg-zinc-800/50 transition-all duration-300 hover:border-purple-500/40 hover:bg-zinc-800/70" 
              : "border-2 border-white/20 p-1 bg-zinc-900"
          }`}>
            <div className="w-full h-full rounded-full overflow-hidden">
              {!isEmpty ? (
                <img
                  src={avatarUrl}
                  alt={`${isLeader ? "Party Leader" : "Participant"} Avatar`}
                  className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                />
              ) : (
                <div className="w-full h-full rounded-full flex items-center justify-center">
                  <div className="text-purple-500/40 group-hover:text-purple-500/60 transition-colors duration-300 text-2xl font-light">
                    <Plus className="h-6 w-6" />
                  </div>
                </div>
              )}
            </div>
            
            {isLeader && (
              <div className="absolute -top-1 -right-1">
                <Crown className="h-5 w-5 text-yellow-500" />
              </div>
            )}
            
            {!isEmpty && (
              <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-zinc-800/90 px-3 py-1 rounded-full text-sm font-medium shadow-lg">
                {username}
              </div>
            )}
          </div>
        </div>
      </TooltipTrigger>
      <TooltipContent>{tooltipText || username}</TooltipContent>
    </Tooltip>
  );
};

interface PlayerSlotsContainerProps {
  participants: string[];
  maxPlayers: number;
  leaderId: string;
  userProfiles: Record<string, UserProfileDTO>;
  currentUser?: { id: string; avatar?: string };
  placeholderAvatar: string;
  onInviteClick?: () => void;
  className?: string;
}

export function PlayerSlotsContainer({
  participants,
  maxPlayers,
  leaderId,
  userProfiles,
  currentUser,
  placeholderAvatar,
  onInviteClick,
  className,
}: PlayerSlotsContainerProps) {
  // Use the hook directly in this component instead of receiving processed data
  const { joinedParticipants, emptySlotsCount, leaderProfile } = usePartyParticipants({
    participants,
    leaderId,
    maxPlayers,
    userProfiles,
    placeholderAvatar,
    currentUser
  });

  return (
    <div className={`relative overflow-hidden rounded-3xl bg-gradient-to-br from-purple-900/50 to-blue-900/50 p-8 shadow-2xl ${className || ""}`}>
      <div className="absolute inset-0 bg-zinc-950/70 backdrop-blur-sm" />
      <div className="relative">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-semibold text-white/90">Party Members</h2>
          <Badge
            variant="secondary"
            className="bg-white/10 text-white px-3 py-1 rounded-full flex items-center gap-2"
          >
            <Users className="h-4 w-4" />
            <span>{participants.length} / {maxPlayers || "?"}</span>
          </Badge>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-6">
          {/* Party Leader Slot */}
          <PlayerSlot 
            avatarUrl={leaderProfile.avatar}
            username={leaderProfile.username}
            tooltipText={`${leaderProfile.username} (Party Leader)`}
            isLeader={true}
            participantId={leaderId}
            currentUserId={currentUser?.id}
          />

          {/* Render Joined Participants */}
          {joinedParticipants.map((participantId, index) => {
            const participantProfile = userProfiles[participantId] || {
              id: participantId,
              username: participantId === currentUser?.id ? "You" : "Player",
              avatar: participantId === currentUser?.id ? (currentUser?.avatar || placeholderAvatar) : placeholderAvatar
            };
            
            return (
              <PlayerSlot
                key={`participant-${participantId}-${index}`}
                avatarUrl={participantProfile.avatar}
                username={participantProfile.username}
                tooltipText={participantProfile.username}
                participantId={participantId}
                currentUserId={currentUser?.id}
              />
            );
          })}

          {/* Render Empty Slots */}
          {emptySlotsCount > 0 &&
            Array.from({ length: emptySlotsCount }).map((_, idx) => (
              <PlayerSlot
                key={`empty-${idx}`}
                avatarUrl=""
                username=""
                tooltipText="Click to invite player"
                isEmpty={true}
                onClick={onInviteClick}
              />
            ))}
        </div>
      </div>
    </div>
  );
}
