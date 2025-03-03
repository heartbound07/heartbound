"use client"

import * as React from "react"
import { Users, Crown, LogOut, GamepadIcon, Trophy, Globe, Mic, Award, Calendar } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Badge } from "@/components/ui/valorant/badge"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate, useParams } from "react-router-dom"
import { deleteParty, getParty, leaveParty } from "@/contexts/valorant/partyService"
import { usePartyUpdates } from "@/contexts/PartyUpdates"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { PlayerSlotsContainer } from "@/components/PlayerSlotsContainer"
import { formatDisplayText, formatBooleanText } from "@/utils/formatters"

// DetailBadge component for displaying details with label and value
const DetailBadge = ({ 
  icon, 
  label, 
  value 
}: { 
  icon: React.ReactNode; 
  label: string; 
  value: string;
}) => {
  return (
    <div className="bg-zinc-800/70 rounded-lg px-3 py-2 flex items-center gap-2 transition-all duration-300 hover:bg-zinc-700/50 group">
      <div className="text-zinc-400 group-hover:text-[#FF4655] transition-colors">
        {icon}
      </div>
      <div>
        <div className="text-xs text-zinc-400">{label}</div>
        <div className="text-sm font-medium text-white">{value}</div>
      </div>
    </div>
  );
};

// Party details section component for better organization
const PartyDetailsSection = ({ party }: { party: any }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      {/* Game Settings Group */}
      <div className="bg-zinc-900/40 rounded-xl p-5 backdrop-blur-sm border border-white/5">
        <h3 className="text-lg font-medium text-white/80 mb-4 flex items-center">
          <GamepadIcon className="mr-2 h-5 w-5 text-[#FF4655]" />
          Game Settings
        </h3>
        <div className="flex flex-wrap gap-3">
          <DetailBadge 
            icon={<Trophy className="h-5 w-5" />} 
            label="Match Type" 
            value={formatDisplayText(party?.matchType)} 
          />
          <DetailBadge 
            icon={<GamepadIcon className="h-5 w-5" />} 
            label="Game Mode" 
            value={formatDisplayText(party?.gameMode)} 
          />
          <DetailBadge 
            icon={<Users className="h-5 w-5" />} 
            label="Team Size" 
            value={formatDisplayText(party?.teamSize)} 
          />
        </div>
      </div>

      {/* Requirements Group */}
      <div className="bg-zinc-900/40 rounded-xl p-5 backdrop-blur-sm border border-white/5">
        <h3 className="text-lg font-medium text-white/80 mb-4 flex items-center">
          <Award className="mr-2 h-5 w-5 text-[#FF4655]" />
          Requirements
        </h3>
        <div className="flex flex-wrap gap-3">
          <DetailBadge 
            icon={<Award className="h-5 w-5" />} 
            label="Rank" 
            value={formatDisplayText(party?.requirements?.rank)} 
          />
          <DetailBadge 
            icon={<Globe className="h-5 w-5" />} 
            label="Region" 
            value={formatDisplayText(party?.requirements?.region)} 
          />
          <DetailBadge 
            icon={<Mic className="h-5 w-5" />} 
            label="Voice Chat" 
            value={formatBooleanText(party?.requirements?.voiceChat)} 
          />
          <DetailBadge 
            icon={<Calendar className="h-5 w-5" />} 
            label="Age" 
            value={formatDisplayText(party?.ageRestriction)} 
          />
          <DetailBadge 
            icon={<Mic className="h-5 w-5" />} 
            label="Voice Preference" 
            value={formatDisplayText(party?.voicePreference)} 
          />
        </div>
      </div>
    </div>
  );
};

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
              console.error("Error getting updated party:", err)
            })
        }
      } catch (error) {
        console.error("Error processing update:", error)
      }
    }
  }, [update, party?.id, navigate, userProfiles])

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

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans flex items-center justify-center">
        <div className="p-8 rounded-xl bg-zinc-900/50 backdrop-blur-sm border border-white/5 shadow-lg flex flex-col items-center">
          <div className="w-12 h-12 rounded-full border-2 border-t-[#FF4655] border-r-[#FF4655]/50 border-b-[#FF4655]/20 border-l-transparent animate-spin mb-4"></div>
          <div className="text-xl font-medium text-white/90">Loading party details...</div>
          <div className="text-sm text-white/60 mt-2">Please wait while we gather the information</div>
        </div>
      </div>
    )
  }

  if (!party) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans flex items-center justify-center">
        <div className="p-8 rounded-xl bg-zinc-900/50 backdrop-blur-sm border border-white/5 shadow-lg text-center">
          <div className="text-xl font-medium text-white/90">Party not found</div>
          <div className="text-sm text-white/60 mt-2">The party you are looking for may have been deleted</div>
          <Button
            className="mt-6 bg-[#FF4655]/90 hover:bg-[#FF4655] text-white"
            onClick={() => navigate("/dashboard/valorant")}
          >
            Return to Dashboard
          </Button>
        </div>
      </div>
    )
  }

  return (
    <TooltipProvider>
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans">
        <div className="container mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6">
          <div className="max-w-5xl mx-auto space-y-4 sm:space-y-6 md:space-y-8">
            {/* Enhanced header with shadow and better spacing */}
            <div className="bg-zinc-900/50 rounded-xl p-6 backdrop-blur-sm border border-white/5 shadow-lg">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <h1 className="text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-white to-white/70">
                  {party.title || "Valorant Party"}
                </h1>
                <div className="flex gap-3">
                  {user?.id === party.userId ? (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-zinc-800/70 hover:bg-zinc-700/90 text-white border border-white/10 hover:border-white/30
                          shadow-md hover:shadow-lg transition-all duration-300 rounded-lg"
                          size="sm"
                          onClick={() => {
                            if (window.confirm("Are you sure you want to delete this party? This action cannot be undone.")) {
                              deleteParty(party.id)
                                .then(() => {
                                  navigate("/dashboard/valorant")
                                })
                                .catch((err: any) => {
                                  console.error("Error deleting party:", err)
                                  alert("Failed to delete party")
                                })
                            }
                          }}
                        >
                          <span className="flex items-center gap-2">
                            <span className="w-5 h-5 flex items-center justify-center text-zinc-400 group-hover:text-white transition-colors">
                              <svg
                                xmlns="http://www.w3.org/2000/svg"
                                width="16"
                                height="16"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              >
                                <path d="M3 6h18"></path>
                                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                              </svg>
                            </span>
                            <span className="font-medium">Delete Party</span>
                          </span>
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className="text-sm text-white">Permanently delete this party</p>
                      </TooltipContent>
                    </Tooltip>
                  ) : (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-zinc-800/70 hover:bg-zinc-700/90 text-white border border-white/10 hover:border-white/30
                          shadow-md hover:shadow-lg transition-all duration-300 rounded-lg"
                          size="sm"
                          onClick={() => {
                            if (window.confirm("Are you sure you want to leave this party?")) {
                              leaveParty(party.id)
                                .then(() => {
                                  navigate("/dashboard/valorant")
                                })
                                .catch((err: any) => {
                                  console.error("Error leaving party:", err)
                                  alert("Failed to leave party")
                                })
                            }
                          }}
                        >
                          <span className="flex items-center gap-2">
                            <LogOut className="h-4 w-4 text-zinc-400 group-hover:text-white transition-colors" />
                            <span className="font-medium">Leave Party</span>
                          </span>
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className="text-sm text-white">Leave this party</p>
                      </TooltipContent>
                    </Tooltip>
                  )}
                </div>
              </div>
              {party.description && (
                <p className="text-zinc-400 mt-3 sm:mt-4 max-w-3xl">
                  {party.description}
                </p>
              )}
            </div>
            
            {/* Reorganized party details section */}
            <PartyDetailsSection party={party} />
            
            {/* Player slots container with improved styling */}
            <PlayerSlotsContainer 
              participants={participants}
              maxPlayers={party?.maxPlayers || 5}
              leaderId={leaderId}
              userProfiles={userProfiles}
              currentUser={user ? { id: user.id, avatar: user.avatar } : undefined}
              placeholderAvatar={placeholderAvatar}
              className="bg-gradient-to-br from-zinc-900/80 to-zinc-900/40 backdrop-blur-md
                border border-white/5 shadow-xl"
              onInviteClick={() => console.log("Invite player clicked")}
            />
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

