"use client"

import React, { useState, useEffect, useRef } from "react"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { Users, GamepadIcon, Mic, Calendar, Trophy, Plus, Award, Globe, ArrowRight, X, Lock } from "lucide-react"
import { joinParty, requestToJoinParty } from "@/contexts/valorant/partyService"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate } from "react-router-dom"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { usePartyUpdates } from "@/contexts/PartyUpdates"

// Helper function to format tooltip text for fields such as gameMode, teamSize, etc.
const formatTooltipText = (text: string | undefined, defaultText: string = "N/A"): string => {
  if (!text) return defaultText
  // Handle region values e.g., "NA_EAST" should become "NA East"
  if (text.includes("_")) {
    const parts = text.split("_")
    if (parts[0] === "NA" && parts.length === 2) {
      return `NA ${parts[1].charAt(0) + parts[1].slice(1).toLowerCase()}`
    }
    // General case: split on underscores and capitalize each part
    return parts.map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(" ")
  }
  // For other values, simply capitalize the first letter
  return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase()
}

// Helper function to format age restriction text
const formatAgeRestriction = (ageText: string | undefined): string => {
  if (!ageText) return "All Ages"
  return ageText === "18_PLUS" ? "18+" : formatTooltipText(ageText)
}

export interface ListingProps {
  party: any; // Replace with a proper type for LFGPartyResponseDTO
}

export default function Listing({ party }: ListingProps) {
  // Dynamically create player slots based on party.maxPlayers and party.participants.
  // We assume party.participants is an array (default to empty array if undefined).
  const participants = party.participants || []
  
  // State to hold user profiles for participants
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({})
  
  // Local state to manage the join process
  const [isJoining, setIsJoining] = useState(false)
  const { user } = useAuth()
  const navigate = useNavigate()
  const { userActiveParty } = usePartyUpdates()
  
  // Placeholder avatar for users without an avatar
  const placeholderAvatar = "/placeholder.svg"
  
  // Reference for the component container
  const listingContainerRef = useRef<HTMLDivElement>(null);
  
  // Add this line to define isInvited
  // Since we don't have the full invited users list in this component,
  // we'll implement a simpler version - considering only the current user
  const isInvited = false; // Default to false since we don't have invitations data in this component
  
  // Check if the current user has already requested to join this party
  const [hasRequestedToJoin, setHasRequestedToJoin] = useState(false);
  
  // Initialize hasRequestedToJoin based on party data
  useEffect(() => {
    if (user?.id && party.joinRequests && party.joinRequests.includes(user.id)) {
      setHasRequestedToJoin(true);
    }
  }, [user?.id, party.joinRequests]);
  
  // Check if the party is invite-only
  const isInviteOnly = party?.requirements?.inviteOnly;
  
  // Fetch user profiles when participants change
  useEffect(() => {
    if (participants.length > 0) {
      // Filter to ensure we only pass string IDs to the API
      const validUserIds = participants.filter((id: any): id is string => typeof id === 'string' && !!id);
      
      if (validUserIds.length > 0) {
        getUserProfiles(validUserIds)
          .then(profiles => {
            setUserProfiles(profiles);
          })
          .catch(error => {
            console.error("Error fetching user profiles:", error);
          });
      }
    }
  }, [participants]);
  
  // Create slots array with profile data
  const slots = Array.from({ length: party.maxPlayers }, (_, i) => {
    const isFilled = i < participants.length;
    const participantId = isFilled ? participants[i] : null;
    const profile = participantId ? userProfiles[participantId] : null;
    
    return {
      id: i + 1,
      filled: isFilled,
      participantId,
      username: profile?.username || `Player ${i + 1}`,
      avatar: profile?.avatar || placeholderAvatar,
    }
  });

  // Check if the current user is already a participant in this party
  const isParticipant = user?.id && party.participants?.includes(user.id)
  
  // Check if the user is already in any party
  const isInAnyParty = !!userActiveParty
  
  // Check if user is the owner of this party
  const isOwner = user?.id === party.userId
  
  // User can join if: they're not already in any party AND they're not already in this specific party
  // AND they haven't already requested to join
  const canJoin = !isInAnyParty && !isParticipant && !isOwner && !hasRequestedToJoin

  // Add state for toast message
  const [toast, setToast] = useState<{message: string, type: 'error' | 'success' | 'info'} | null>(null)

  // Handle the Join Game Button click for non-owners
  const handleJoinGame = async () => {
    // If user is already in a party, show error toast and don't proceed
    if (isInAnyParty) {
      setToast({
        message: "You must leave your current party before joining another one",
        type: "error"
      });
      
      // Auto-dismiss toast after 3 seconds
      setTimeout(() => setToast(null), 3000);
      return;
    }
    
    try {
      setIsJoining(true);
      
      // If it's an invite-only party, send a request to join instead
      if (isInviteOnly) {
        await requestToJoinParty(party.id);
        setHasRequestedToJoin(true);
        setToast({
          message: "Request sent! The party leader will be notified of your interest.",
          type: "success"
        });
        // Navigate to party details page after requesting to join
        navigate(`/dashboard/valorant/party/${party.id}`);
      } else {
        // For open parties, join directly as before
        await joinParty(party.id);
        // Redirect to the party page after a successful join
        navigate(`/dashboard/valorant/party/${party.id}`);
      }
    } catch (error: any) {
      setToast({
        message: error.message || "Failed to join party",
        type: "error"
      });
    } finally {
      setIsJoining(false);
      // Auto-dismiss toast after 3 seconds
      setTimeout(() => setToast(null), 3000);
    }
  };

  // Handle the "View Party" navigation for owners and existing participants
  const handleViewParty = () => {
    // Redirect owners or participants to the party details page using the correct route
    navigate(`/dashboard/valorant/${party.id}`)
  }

  // Get the appropriate button text based on user's status
  const getButtonText = () => {
    if (isOwner || isParticipant) {
      return (
        <>
          <span>View</span>
          <ArrowRight className="h-3 w-3" />
        </>
      );
    } else if (hasRequestedToJoin) {
      return <span>Waiting...</span>;
    } else if (isInviteOnly) {
      return (
        <>
          <span>Request Join</span>
          <ArrowRight className="h-3 w-3" />
        </>
      );
    } else {
      return (
        <>
          <span>Join</span>
          <ArrowRight className="h-3 w-3" />
        </>
      );
    }
  };

  return (
    <div 
      ref={listingContainerRef} 
      className="overflow-hidden bg-[#0F1923] rounded-lg shadow-xl border border-white/5 hover:border-white/10 transition-all duration-300 transform hover:-translate-y-1 h-full flex flex-col"
    >
      {/* Header section with title and description */}
      <div className="px-5 py-4 bg-gradient-to-r from-[#1F2731] to-[#0F1923] border-b border-zinc-700/30">
        <h1 className="text-sm font-semibold text-white tracking-wide">
          {party.title}
        </h1>
        <p className="text-xs text-zinc-400 mt-1 line-clamp-2">
          {party.description}
        </p>
      </div>
      
      {/* Party Details Icons Row */}
      <div className="flex items-center justify-between py-3 bg-[#1F2731]/30 px-4">
        {/* Left side icons group */}
        <div className="flex items-center gap-1">
          <TooltipProvider>
            {/* Game Mode */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <GamepadIcon className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatTooltipText(party.gameMode, "Unrated")}</p>
              </TooltipContent>
            </Tooltip>

            {/* Team Size */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Users className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatTooltipText(party.teamSize, "Duo")}</p>
              </TooltipContent>
            </Tooltip>

            {/* Invite Only */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Lock className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{party?.requirements?.inviteOnly ? "Invite Only" : "Open to Join"}</p>
              </TooltipContent>
            </Tooltip>

            {/* Age Restriction */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Calendar className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatAgeRestriction(party.ageRestriction)}</p>
              </TooltipContent>
            </Tooltip>

            {/* Match Type */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Trophy className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatTooltipText(party.matchType, "Casual")}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>

        {/* Right side - Region and Rank icons */}
        <div className="flex items-center gap-1">
          <TooltipProvider>
            {/* Rank */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Award className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatTooltipText(party?.requirements?.rank)}</p>
              </TooltipContent>
            </Tooltip>

            {/* Region */}
            <Tooltip>
              <TooltipTrigger asChild>
                <div className="p-2 rounded-full hover:bg-white/5 transition-colors duration-200 cursor-pointer">
                  <Globe className="h-4 w-4 text-[#8B97A4]" />
                </div>
              </TooltipTrigger>
              <TooltipContent side="left" sideOffset={5} className="bg-[#1F2731] border border-white/10">
                <p className="text-xs font-medium">{formatTooltipText(party?.requirements?.region)}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </div>

      {/* Player Slots Section - Redesigned with left-aligned players */}
      <div className="p-5 flex-1 flex flex-col">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Users className="h-4 w-4 text-[#FF4655]" />
            <span className="text-xs font-medium text-white">Players</span>
          </div>
          <span className="text-xs font-medium text-white bg-[#1F2731] py-1 px-2.5 rounded-full">
            {participants.length} <span className="text-[#8B97A4]">/</span> {party.maxPlayers}
          </span>
        </div>
        
        {/* Flex container for players and action button */}
        <div className="flex items-center justify-between gap-4 mt-4 flex-grow">
          {/* Player Avatars - Now left-aligned with slightly larger size */}
          <div className="flex-1">
            <div className="flex flex-wrap justify-start gap-2">
              {slots.map((slot) => (
                <TooltipProvider key={slot.id}>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <div
                        className={`relative w-10 h-10 rounded-full group 
                          ${slot.filled 
                            ? "bg-[#1F2731] ring-2 ring-[#FF4655]/30 hover:ring-[#FF4655]/60 cursor-pointer transition-all duration-300 transform hover:scale-105" 
                            : "border-2 border-dashed border-[#1F2731] hover:border-[#FF4655]/30 transition-all duration-300 flex items-center justify-center"
                          }`}
                      >
                        {slot.filled ? (
                          <Avatar className="h-full w-full">
                            <AvatarImage src={slot.avatar} alt={slot.username} />
                            <AvatarFallback className="bg-[#1F2731] text-white">{slot.username.charAt(0).toUpperCase()}</AvatarFallback>
                          </Avatar>
                        ) : (
                          <Plus className="h-3.5 w-3.5 text-[#1F2731] group-hover:text-[#FF4655]/50 transition-colors duration-200" />
                        )}
                      </div>
                    </TooltipTrigger>
                    <TooltipContent side="left" className="bg-[#1F2731] border border-white/10">
                      <p className="text-xs font-medium">{slot.filled ? slot.username : "Empty Slot"}</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              ))}
            </div>
          </div>
          
          {/* Join/View Button - Positioned to the right */}
          <div className="flex-shrink-0">
            <Button
              onClick={isOwner || isParticipant ? handleViewParty : handleJoinGame}
              disabled={isJoining || (!isOwner && !isParticipant && !canJoin)}
              className={`py-2 px-3 h-auto text-xs font-semibold tracking-wide transition-all 
                duration-300 ease-in-out transform hover:scale-[1.05] shadow-md
                focus:outline-none focus:ring-2 focus:ring-[#FF4655]/50 focus:ring-opacity-50 
                rounded-md flex items-center gap-1 ${
                  !canJoin && !isOwner && !isParticipant && !hasRequestedToJoin
                  ? "bg-gray-500 cursor-not-allowed" 
                  : hasRequestedToJoin
                    ? "bg-[#FF4655]/50 hover:bg-[#FF4655]/60 text-white"
                    : "bg-[#FF4655] hover:bg-[#FF4655]/90 text-white"
                }`}
            >
              {isJoining ? <span>Processing...</span> : getButtonText()}
            </Button>
          </div>
        </div>
      </div>

      {/* Toast Notification */}
      {toast && (
        <div className={`fixed top-4 right-4 z-50 ${
          toast.type === 'error' ? 'bg-[#FF4655]' : 
          toast.type === 'success' ? 'bg-green-500' : 'bg-blue-500'
        } text-white px-4 py-3 rounded-lg shadow-lg flex items-center justify-between animate-fadeIn`}>
          <span>{toast.message}</span>
          <X className="ml-3 h-4 w-4 cursor-pointer" onClick={() => setToast(null)} />
        </div>
      )}
    </div>
  )
}

