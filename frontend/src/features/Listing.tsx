"use client"

import React, { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { Users, GamepadIcon, Mic, Calendar, Trophy, Plus, Award, Globe } from "lucide-react"
import { joinParty } from "@/contexts/valorant/partyService"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate } from "react-router-dom"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"

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
  
  // Placeholder avatar for users without an avatar
  const placeholderAvatar = "/placeholder.svg"
  
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

  // Determine if the current user is the party owner
  const isOwner = user?.id === party.userId

  // Handle the Join Game Button click for non-owners
  const handleJoinGame = async () => {
    setIsJoining(true)
    try {
      // Call the joinParty function with the party ID
      const result = await joinParty(party.id)
      console.log("Joined party successfully:", result)
      // Redirect the user to the party details page
      navigate(`/dashboard/valorant/${party.id}`)
    } catch (error) {
      console.error("Error joining the party", error)
      // Optionally display an error notification here
    } finally {
      setIsJoining(false)
    }
  }

  // Handle the "View Party" navigation for owners
  const handleViewParty = () => {
    // Redirect owners to the party details page using the correct route
    navigate(`/dashboard/valorant/${party.id}`)
  }

  return (
    <div className="w-full max-w-4xl mx-auto bg-zinc-900 rounded-lg overflow-hidden shadow-lg">
      <div className="px-4 py-3 bg-gradient-to-r from-zinc-900 to-zinc-800 border-b border-zinc-700/50">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-sm font-semibold text-zinc-100 tracking-wide mt-2">
              {party.title}
            </h1>
            <p className="text-xs text-zinc-400 mt-1">
              {party.description}
            </p>
          </div>
        </div>
      </div>
      
      {/* Party Details Tooltip Row */}
      <div className="flex items-center justify-start space-x-4 py-2 bg-zinc-800 pl-4">
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <GamepadIcon className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatTooltipText(party.gameMode, "Unrated")}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Users className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatTooltipText(party.teamSize, "Duo")}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Mic className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{party.voicePreference || "Discord"}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Calendar className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatAgeRestriction(party.ageRestriction)}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Trophy className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatTooltipText(party.matchType, "Casual")}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        {/* Rank Tooltip */}
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors 
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Award className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatTooltipText(party?.requirements?.rank)}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        {/* Region Tooltip */}
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors 
                  focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 
                  [&_svg]:pointer-events-none h-9 w-9 text-zinc-400 hover:text-white hover:bg-transparent"
              >
                <Globe className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              <span>{formatTooltipText(party?.requirements?.region)}</span>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      <div className="p-4">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2">
            <Users className="h-4 w-4 text-zinc-400" />
            <span className="text-xs font-medium text-zinc-300">Players</span>
          </div>
          <span className="text-xs font-medium text-zinc-400">
            {participants.length} / {party.maxPlayers}
          </span>
        </div>
        <div className="flex space-x-2 mb-4">
          {slots.map((slot) => (
            <TooltipProvider key={slot.id}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <div
                    className={`relative w-10 h-10 rounded-full ${
                      slot.filled
                        ? "bg-zinc-800 ring-1 ring-violet-500/50"
                        : "border border-dashed border-zinc-700 hover:border-zinc-500"
                    } transition-all duration-200 group cursor-pointer flex items-center justify-center`}
                  >
                    {slot.filled ? (
                      <Avatar className="h-full w-full">
                        <AvatarImage src={slot.avatar} />
                        <AvatarFallback>P{slot.id}</AvatarFallback>
                      </Avatar>
                    ) : (
                      <Plus className="h-4 w-4 text-zinc-600 group-hover:text-zinc-400 transition-colors duration-200" />
                    )}
                  </div>
                </TooltipTrigger>
                <TooltipContent>
                  <p>{slot.filled ? slot.username : "Empty Slot"}</p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          ))}
        </div>
        <Button
          onClick={isOwner ? handleViewParty : handleJoinGame}
          disabled={isJoining}
          className="w-full bg-[#FF4655] hover:bg-[#FF4655]/90 text-white py-2 text-xs font-semibold tracking-wide transition-all duration-200 ease-in-out transform hover:scale-[1.02] focus:outline-none focus:ring-2 focus:ring-[#FF4655] focus:ring-opacity-50"
        >
          {isOwner ? "View Party" : (isJoining ? "Joining..." : "Join Game")}
        </Button>
      </div>
    </div>
  )
}

