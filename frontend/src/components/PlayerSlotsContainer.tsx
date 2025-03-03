"use client"

import * as React from "react"
import { Users, Crown, Plus, Shield } from "lucide-react"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
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
    ? "from-indigo-600 via-blue-500 to-indigo-600" 
    : isEmpty 
      ? "" 
      : isCurrentUser 
        ? "from-amber-500 via-orange-500 to-amber-500"
        : "from-emerald-500 via-teal-500 to-emerald-500";

  // Determine border color based on slot type
  const borderColor = isLeader
    ? "border-blue-400/50"
    : isEmpty
      ? "border-[#FF4655]/20"
      : isCurrentUser
        ? "border-amber-400/50"
        : "border-emerald-400/50";

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div 
          className={`relative group ${!isEmpty ? "cursor-default" : "cursor-pointer"}`}
          onClick={isEmpty ? onClick : undefined}
        >
          {!isEmpty && (
            <div className={`absolute -inset-0.5 bg-gradient-to-r ${gradientColors} rounded-full 
              opacity-70 group-hover:opacity-100 transition duration-300 animate-pulse-slow blur-sm`} />
          )}
          
          <div className={`relative w-full aspect-square rounded-full ${
            isEmpty 
              ? `border-2 border-[#FF4655]/20 p-1 bg-zinc-800/50 transition-all duration-300 
                hover:border-[#FF4655]/40 hover:bg-zinc-800/70 group-hover:scale-105` 
              : `border-2 ${borderColor} p-1 bg-zinc-900 group-hover:scale-105 transition-transform duration-300`
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
                  <div className="text-[#FF4655]/40 group-hover:text-[#FF4655]/80 transition-colors duration-300">
                    <Plus className="h-7 w-7" />
                  </div>
                </div>
              )}
            </div>
            
            {isLeader && (
              <div className="absolute -top-1 -right-1 bg-gradient-to-br from-yellow-400 to-yellow-600 rounded-full p-0.5 shadow-lg">
                <Crown className="h-4 w-4 text-zinc-900" />
              </div>
            )}
            
            {isCurrentUser && !isLeader && (
              <div className="absolute -top-1 -left-1 bg-gradient-to-br from-amber-400 to-orange-500 rounded-full p-0.5 shadow-lg">
                <Shield className="h-4 w-4 text-zinc-900" />
              </div>
            )}
            
            {!isEmpty && (
              <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-zinc-900/90 px-3 py-1 
                rounded-full text-sm font-medium shadow-lg border border-white/10 truncate max-w-full"
                style={{ minWidth: '80%' }}>
                <span className="truncate block text-center">{username}</span>
              </div>
            )}
          </div>
        </div>
      </TooltipTrigger>
      <TooltipContent className="bg-zinc-900 border border-white/10 shadow-xl px-3 py-2">
        <div className="flex flex-col items-center">
          {isLeader && (
            <Badge variant="valorant" size="sm" className="mb-1 bg-gradient-to-r from-yellow-500 to-amber-500 text-zinc-900 font-bold">
              Party Leader
            </Badge>
          )}
          {isCurrentUser && !isLeader && (
            <Badge variant="valorant" size="sm" className="mb-1 bg-gradient-to-r from-orange-500 to-amber-500 text-zinc-900 font-bold">
              You
            </Badge>
          )}
          <span className="font-medium">{tooltipText || username}</span>
        </div>
      </TooltipContent>
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
    <div className={`relative overflow-hidden rounded-2xl bg-gradient-to-br from-zinc-900/80 to-zinc-900/40 p-6 md:p-8 shadow-xl ${className || ""}`}>
      <div className="absolute inset-0 bg-zinc-950/70 backdrop-blur-sm" />
      <div className="relative">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between mb-6 md:mb-8">
          <div className="flex items-center gap-2 mb-3 sm:mb-0">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#FF4655] to-[#FF8F97] flex items-center justify-center shadow-lg">
              <Users className="h-4 w-4 text-white" />
            </div>
            <h2 className="text-2xl font-bold text-white">Party Members</h2>
          </div>
          <Badge
            variant="valorantCount"
            className="bg-zinc-800/80 text-white px-4 py-2 rounded-xl border border-[#FF4655]/20 flex items-center gap-3 shadow-md"
          >
            <span className="text-[#FF4655] font-bold">{participants.length}</span>
            <span className="text-zinc-400">/</span>
            <span className="text-white">{maxPlayers || "?"}</span>
          </Badge>
        </div>
        
        {/* Enhanced grid with better responsiveness */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 md:gap-6">
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
        
        {/* Optional helper text for empty slots */}
        {emptySlotsCount > 0 && (
          <div className="mt-6 text-center text-sm text-zinc-400 italic">
            Click on an empty slot to invite more players to your party
          </div>
        )}
      </div>
    </div>
  );
}
