"use client"

import * as React from "react"
import { Users, Crown, Plus, UserCheck } from "lucide-react"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { type UserProfileDTO } from "@/config/userService"
import { usePartyParticipants } from "@/hooks/usePartyParticipants"
import "../assets/animations.css"

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
  isNewJoin?: boolean;
}

const PlayerSlot: React.FC<PlayerSlotProps> = ({
  avatarUrl,
  username,
  tooltipText,
  isLeader = false,
  isEmpty = false,
  onClick,
  currentUserId,
  participantId,
  isNewJoin = false,
}) => {
  const isCurrentUser = currentUserId && participantId && currentUserId === participantId;
  
  // Updated gradient colors with premium red for leader
  const gradientColors = isLeader 
    ? "from-red-900 via-red-700 to-red-800" 
    : isEmpty 
      ? "" 
      : isCurrentUser 
        ? "from-amber-600 via-amber-500 to-amber-700"
        : "from-[#1F2731] via-[#2C3A47] to-[#1F2731]";

  // Updated border colors to match new gradients
  const borderColor = isLeader
    ? "border-red-600/50"
    : isEmpty
      ? "border-[#FF4655]/20"
      : isCurrentUser
        ? "border-amber-500/50"
        : "border-[#1F2731]/50";
        
  // Animation classes
  const animationClasses = isNewJoin ? "animate-partyJoin" : "";
  const highlightClasses = isNewJoin ? "animate-joinHighlight" : "";

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div 
          className={`relative group ${!isEmpty ? "cursor-default" : "cursor-pointer"} ${animationClasses} max-w-[100px] mx-auto w-full`}
          onClick={isEmpty ? onClick : undefined}
        >
          {!isEmpty && (
            <div className={`absolute -inset-0.5 bg-gradient-to-r ${gradientColors} rounded-full 
              opacity-70 group-hover:opacity-100 transition duration-300 animate-pulse-slow blur-sm`} />
          )}
          
          <div className={`relative w-full aspect-square rounded-full ${highlightClasses} ${
            isEmpty 
              ? `border-2 border-[#FF4655]/20 p-1.5 bg-[#1F2731]/50 transition-all duration-300 
                hover:border-[#FF4655]/40 hover:bg-[#1F2731]/70 group-hover:scale-105` 
              : `border-2 ${borderColor} p-1.5 bg-[#0F1923] group-hover:scale-105 transition-transform duration-300`
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
                    <Plus className="h-6 w-6" />
                  </div>
                </div>
              )}
            </div>
            
            {isLeader && (
              <div className="absolute -top-1.5 -right-1.5 bg-gradient-to-br from-red-700 to-red-900 rounded-full p-0.5 shadow-lg">
                <Crown className="h-4 w-4 text-white" />
              </div>
            )}
            
            {!isEmpty && (
              <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-[#1F2731]/90 px-2.5 py-0.5 
                rounded-full text-xs font-medium shadow-lg border border-white/10 truncate max-w-full"
                style={{ minWidth: '85%' }}>
                <span className="truncate block text-center">{username}</span>
              </div>
            )}
          </div>
        </div>
      </TooltipTrigger>
      <TooltipContent className="bg-[#1F2731] border border-white/10 shadow-xl px-3 py-2 z-[100]">
        <div className="flex flex-col items-center">
          {isLeader && (
            <Badge variant="valorant" size="sm" className="mb-1 bg-gradient-to-r from-[#FF4655] to-[#FF8F97] text-white font-bold">
              Party Leader
            </Badge>
          )}
          {isCurrentUser && !isLeader && (
            <Badge variant="valorant" size="sm" className="mb-1 bg-gradient-to-r from-[#FF4655] to-[#FF8F97] text-white font-bold">
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
  // Store previous participants for animation
  const [prevParticipants, setPrevParticipants] = React.useState<string[]>([]);
  
  // Use the hook directly in this component instead of receiving processed data
  const { joinedParticipants, emptySlotsCount, leaderProfile } = usePartyParticipants({
    participants,
    leaderId,
    maxPlayers,
    userProfiles,
    placeholderAvatar,
    currentUser
  });
  
  // Find newly joined participants
  const newJoins = React.useMemo(() => {
    return participants.filter(id => !prevParticipants.includes(id));
  }, [participants, prevParticipants]);
  
  // Update previous participants after checking for new joins
  React.useEffect(() => {
    // Set a small delay to ensure animation plays
    const timer = setTimeout(() => {
      setPrevParticipants(participants);
    }, 100);
    
    return () => clearTimeout(timer);
  }, [participants]);

  // Calculate if party is full
  const isFull = participants.length === maxPlayers;
  
  // Calculate total slots (filled + empty) for better grid sizing
  const totalSlots = 1 + joinedParticipants.length + emptySlotsCount; // 1 for leader
  
  // Determine optimal grid columns based on total slots
  // This ensures better centering for small teams
  const getGridCols = () => {
    if (totalSlots <= 2) {
      return "grid-cols-1 sm:grid-cols-2";
    } else if (totalSlots <= 3) {
      return "grid-cols-1 sm:grid-cols-3";
    } else if (totalSlots <= 5) {
      return "grid-cols-2 sm:grid-cols-3 md:grid-cols-5";
    } else {
      return "grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5";
    }
  };

  return (
    <div className={`relative overflow-hidden rounded-xl bg-[#1F2731]/40 p-5 backdrop-blur-sm border border-white/5 shadow-xl ${className || ""}`}>
      <div className="relative">
        {/* Player counter badge remains at the top-right */}
        <div className="flex justify-end mb-6">
          <Tooltip>
            <TooltipTrigger asChild>
              <div className={`relative overflow-hidden px-3 py-1.5 rounded-lg bg-gradient-to-br from-[#1F2731]/90 to-[#0F1923]/90 border ${isFull ? 'border-[#FF4655]/50' : 'border-[#8B97A4]/30'} shadow-lg group transition-all duration-300 hover:shadow-xl`}>
                {isFull && (
                  <div className="absolute inset-0 bg-gradient-to-r from-[#FF4655]/10 to-transparent animate-pulse-slow"></div>
                )}
                
                <div className="relative flex items-center gap-2">
                  <div className="flex items-center">
                    <UserCheck className={`h-3.5 w-3.5 mr-1.5 ${isFull ? 'text-[#FF4655]' : 'text-[#8B97A4]'}`} />
                    <span className={`text-base font-bold ${isFull ? 'text-[#FF4655]' : 'text-white'}`}>
                      {participants.length}
                    </span>
                    <span className={`mx-1 ${isFull ? 'text-[#FF4655]' : 'text-[#8B97A4]'}`}>/</span>
                    <span className={`text-base font-medium ${isFull ? 'text-[#FF4655]' : 'text-white'}`}>{maxPlayers || "?"}</span>
                  </div>
                  
                  {/* Status indicator */}
                  <div className={`hidden sm:block px-1.5 py-0.5 text-xs rounded-full font-medium ${
                    isFull 
                      ? 'bg-[#FF4655]/20 text-[#FF4655]' 
                      : 'bg-[#1F2731]/70 text-[#8B97A4]'
                  }`}>
                    {isFull ? 'FULL' : 'OPEN'}
                  </div>
                </div>
              </div>
            </TooltipTrigger>
            <TooltipContent className="bg-[#1F2731] border border-white/10 z-[100]">
              <p className="text-sm">{isFull ? 'Party is full' : `${emptySlotsCount} slot${emptySlotsCount !== 1 ? 's' : ''} available`}</p>
            </TooltipContent>
          </Tooltip>
        </div>
        
        {/* Wrapper div for centering the grid */}
        <div className="flex justify-center">
          {/* Dynamic grid with proper centering based on team size */}
          <div className={`grid ${getGridCols()} gap-6 md:gap-8 w-fit mx-auto`}>
            {/* Party Leader Slot */}
            <PlayerSlot 
              avatarUrl={leaderProfile.avatar}
              username={leaderProfile.username}
              tooltipText={`${leaderProfile.username} (Party Leader)`}
              isLeader={true}
              participantId={leaderId}
              currentUserId={currentUser?.id}
              isNewJoin={newJoins.includes(leaderId)}
            />

            {/* Joined Participants */}
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
                  isNewJoin={newJoins.includes(participantId)}
                />
              );
            })}

            {/* Empty Slots */}
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
        
        {/* Helper text */}
        {emptySlotsCount > 0 && (
          <div className="mt-6 text-center text-xs text-[#8B97A4] italic">
            Click on an empty slot to invite more players to your party
          </div>
        )}
      </div>
    </div>
  );
}
