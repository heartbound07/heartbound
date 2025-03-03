"use client"

import * as React from "react"
import { Users, Crown, LogOut, GamepadIcon, Trophy, Globe, Mic, Award } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate, useParams } from "react-router-dom"
import { deleteParty, getParty, leaveParty } from "@/contexts/valorant/partyService"
import { usePartyUpdates } from "@/contexts/PartyUpdates"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"

export default function ValorantPartyDetails() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { partyId } = useParams<{ partyId: string }>()
  const { update } = usePartyUpdates()

  const [party, setParty] = React.useState<any>(null)
  const [userProfiles, setUserProfiles] = React.useState<Record<string, UserProfileDTO>>({})
  const [isLoading, setIsLoading] = React.useState(true)

  // Placeholder avatar for participants who don't have an available avatar.
  const placeholderAvatar = "https://v0.dev/placeholder.svg?height=400&width=400"

  // Initial party fetch
  React.useEffect(() => {
    if (partyId) {
      setIsLoading(true)
      getParty(partyId)
        .then((data) => {
          setParty(data)
          
          // Ensure participants is handled as an array regardless of how it's received
          const participants = data.participants || [];
          // Add explicit type assertion and filter to ensure we only pass strings
          const userIdsToFetch = (Array.isArray(participants) 
            ? participants 
            : (typeof participants === 'object' ? Object.values(participants) : []))
            .filter((id): id is string => typeof id === 'string');
          
          return getUserProfiles(userIdsToFetch)
        })
        .then((profiles) => {
          setUserProfiles(profiles)
          setIsLoading(false)
        })
        .catch((err: any) => {
          console.error("Error fetching party:", err)
          setIsLoading(false)
        })
    }
  }, [partyId])

  // Listen for party updates
  React.useEffect(() => {
    if (update && party?.id) {
      try {
        // If update is already an object, use it directly; otherwise, parse it.
        const updateObj = typeof update === "string" ? JSON.parse(update) : update
        
        // Handle party deletion event
        if (updateObj?.eventType === "PARTY_DELETED") {
          // Check if this deletion affects the current party
          if (update.party?.id === party.id || !update.party) {
            console.info("Party has been deleted, redirecting to dashboard")
            navigate("/dashboard/valorant")
            return  // Early return to avoid further processing
          }
        }
        
        // Check for other relevant event types
        if (updateObj?.eventType && ["PARTY_JOINED", "PARTY_UPDATED", "PARTY_LEFT"].includes(updateObj.eventType)) {
          getParty(party.id)
            .then((data) => {
              setParty(data)
              
              // Fetch profiles for any new participants
              const currentProfileIds = Object.keys(userProfiles)
              const newParticipantIds = data.participants.filter(
                (id: string) => !currentProfileIds.includes(id)
              )
              
              if (newParticipantIds.length > 0) {
                // Make sure we're filtering to get only string IDs
                const validNewParticipantIds = newParticipantIds.filter((id): id is string => typeof id === 'string');
                return getUserProfiles(validNewParticipantIds).then(newProfiles => {
                  setUserProfiles(prev => ({...prev, ...newProfiles}))
                })
              }
            })
            .catch((err: any) => {
              console.error("Error re-fetching party on update:", err)
              // If we get a 404 error, the party might have been deleted
              if (err.response?.status === 404) {
                console.info("Party not found, redirecting to dashboard")
                navigate("/dashboard/valorant")
              }
            })
        }
      } catch (error) {
        console.error("Error parsing update in ValorantPartyDetails:", error)
      }
    }
  }, [update, party?.id, userProfiles, navigate])  // Added navigate to dependency array

  const handleLeaveGroup = async () => {
    if (!partyId) {
      console.error("Party ID is not available")
      return
    }
    try {
      if (user && party && user.id === party.userId) {
        // Party leader leaving: delete the party.
        await deleteParty(partyId)
      } else {
        // Non-leader leaving: remove self from the party.
        await leaveParty(partyId)
      }
      navigate("/dashboard/valorant")
    } catch (error) {
      console.error("Error leaving group:", error)
    }
  }

  // Add debug log before calculating participants details
  console.debug("Party data:", party);
  console.debug("Participants raw:", party?.participants);

  // Calculate participants details.
  const leaderId = party?.userId
  // Ensure participants is always handled as an array
  const participants: string[] = party?.participants 
    ? (Array.isArray(party.participants) 
       ? Array.from(party.participants) 
       : Object.values(party.participants))
    : [];
  const joinedParticipants = participants.filter((p) => p !== leaderId)
  // Total slots are party.maxPlayers. One slot is reserved for the leader.
  const emptySlotsCount = party?.maxPlayers - (1 + joinedParticipants.length)

  // Get the leader profile
  const leaderProfile = userProfiles[leaderId] || {
    avatar: placeholderAvatar,
    username: "Leader",
  }

  // Helper function to format text for display
  const formatDisplayText = (text: string | undefined, defaultText: string = "N/A"): string => {
    if (!text) return defaultText;
    
    // Handle region values with underscores (e.g., "NA_EAST" to "NA East")
    if (text.includes("_")) {
      return text.split("_").map(part => 
        part.charAt(0) + part.slice(1).toLowerCase()
      ).join(" ");
    }
    
    // Default case: capitalize first letter
    return text.charAt(0).toUpperCase() + text.slice(1).toLowerCase();
  }

  // Convert boolean to Yes/No text
  const formatBooleanText = (value: boolean | undefined): string => {
    if (value === undefined || value === null) return "N/A";
    return value ? "Yes" : "No";
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#0F1923] text-white font-sans flex items-center justify-center">
        <div className="text-xl">Loading party details...</div>
      </div>
    )
  }

  return (
    <TooltipProvider>
      <div className="min-h-screen bg-[#0F1923] text-white font-sans">
        {/* Background elements */}
        <div className="fixed inset-0 bg-[#0F1923] z-0">
          <div className="absolute inset-0 bg-gradient-to-br from-[#FF4655]/10 to-transparent opacity-50"></div>
          <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-[#1F2731] to-transparent opacity-30"></div>
        </div>

        {/* Content */}
        <div className="relative z-10">
          {/* Header and other sections omitted for brevity */}
          <div className="max-w-6xl mx-auto p-6">
            {/* Leave button section */}
            <div className="flex items-center justify-end mb-4">
              <div className="flex gap-3">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button
                      variant="secondary"
                      size="icon"
                      onClick={handleLeaveGroup}
                      className="bg-white/10 hover:bg-white/20 transition-all duration-300 rounded-full"
                    >
                      <LogOut className="h-4 w-4" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>Leave group</TooltipContent>
                </Tooltip>
              </div>
            </div>

            {/* Party details badges section */}
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 mb-8">
              {/* Match Type Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Trophy className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.matchType)}</span>
                </Badge>
              </div>
              
              {/* Game Mode Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <GamepadIcon className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.gameMode)}</span>
                </Badge>
              </div>
              
              {/* Team Size Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Users className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.teamSize)}</span>
                </Badge>
              </div>
              
              {/* Voice Preference Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Mic className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.voicePreference)}</span>
                </Badge>
              </div>
              
              {/* Age Restriction Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <span className="font-medium">{formatDisplayText(party?.ageRestriction)}</span>
                </Badge>
              </div>
              
              {/* Required Rank Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Award className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.requirements?.rank)}</span>
                </Badge>
              </div>
              
              {/* Required Region Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Globe className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">{formatDisplayText(party?.requirements?.region)}</span>
                </Badge>
              </div>
              
              {/* Voice Chat Required Badge */}
              <div className="group relative">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
                <Badge variant="secondary" className="relative w-full bg-zinc-900/80 hover:bg-zinc-800/90 shadow-lg border border-[#FF4655]/20 group-hover:border-[#FF4655]/40 transition-all duration-300 text-white/90 group-hover:text-white px-4 py-2 text-sm rounded-full inline-flex items-center justify-center gap-2">
                  <Mic className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />
                  <span className="font-medium">Voice Chat: {formatBooleanText(party?.requirements?.voiceChat)}</span>
                </Badge>
              </div>
            </div>

            {/* Player Slots Container */}
            <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-purple-900/50 to-blue-900/50 p-8 shadow-2xl mt-8">
              <div className="absolute inset-0 bg-zinc-950/70 backdrop-blur-sm" />
              <div className="relative">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-2xl font-semibold text-white/90">Party Members</h2>
                  <Badge
                    variant="secondary"
                    className="bg-white/10 text-white px-3 py-1 rounded-full flex items-center gap-2"
                  >
                    <Users className="h-4 w-4" />
                    <span>{participants.length} / {party?.maxPlayers || "?"}</span>
                  </Badge>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-6">
                  {/* Party Leader Slot */}
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <div className="relative group">
                        <div className="absolute -inset-0.5 bg-gradient-to-r from-purple-600 to-blue-600 rounded-full opacity-75 group-hover:opacity-100 transition duration-300 blur" />
                        <div className="relative w-full aspect-square rounded-full border-2 border-white/20 p-1 bg-zinc-900">
                          <div className="w-full h-full rounded-full overflow-hidden">
                            <img
                              src={leaderProfile.avatar}
                              alt="Party Leader Avatar"
                              className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                            />
                          </div>
                          <div className="absolute -top-1 -right-1">
                            <Crown className="h-5 w-5 text-yellow-500" />
                          </div>
                        </div>
                        <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-zinc-800/90 px-3 py-1 rounded-full text-sm font-medium shadow-lg">
                          {`${leaderProfile.username}`}
                        </div>
                      </div>
                    </TooltipTrigger>
                    <TooltipContent>{`${leaderProfile.username} (Party Leader)`}</TooltipContent>
                  </Tooltip>

                  {/* Render Joined Participants */}
                  {joinedParticipants.map((participantId, index) => {
                    const participantProfile = userProfiles[participantId] || {
                      id: participantId,
                      username: participantId === user?.id ? "You" : "Player",
                      avatar: participantId === user?.id ? (user?.avatar || placeholderAvatar) : placeholderAvatar
                    }
                    
                    return (
                      <Tooltip key={index}>
                        <TooltipTrigger asChild>
                          <div className="relative group cursor-pointer">
                            <div className="absolute -inset-0.5 bg-gradient-to-r from-green-500 to-blue-500 rounded-full opacity-75 group-hover:opacity-100 transition duration-300 blur" />
                            <div className="relative w-full aspect-square rounded-full border-2 border-white/20 p-1 bg-zinc-900">
                              <div className="w-full h-full rounded-full overflow-hidden">
                                <img
                                  src={participantProfile.avatar}
                                  alt="Participant Avatar"
                                  className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                                />
                              </div>
                            </div>
                            <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-zinc-800/90 px-3 py-1 rounded-full text-sm font-medium shadow-lg">
                              {participantProfile.username}
                            </div>
                          </div>
                        </TooltipTrigger>
                        <TooltipContent>{participantProfile.username}</TooltipContent>
                      </Tooltip>
                    )
                  })}

                  {/* Render Empty Slots */}
                  {emptySlotsCount > 0 &&
                    Array.from({ length: emptySlotsCount }).map((_, idx) => (
                      <Tooltip key={`empty-${idx}`}>
                        <TooltipTrigger asChild>
                          <div className="relative group cursor-pointer">
                            <div className="w-full aspect-square rounded-full border-2 border-purple-500/20 p-1 bg-zinc-800/50 transition-all duration-300 hover:border-purple-500/40 hover:bg-zinc-800/70">
                              <div className="w-full h-full rounded-full flex items-center justify-center">
                                <div className="text-purple-500/40 group-hover:text-purple-500/60 transition-colors duration-300 text-2xl font-light">
                                  +
                                </div>
                              </div>
                            </div>
                          </div>
                        </TooltipTrigger>
                        <TooltipContent>Click to invite player</TooltipContent>
                      </Tooltip>
                    ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

