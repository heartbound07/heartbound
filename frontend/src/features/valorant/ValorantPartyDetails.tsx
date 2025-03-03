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

// IconTooltipButton component with larger icons for better visibility
const IconTooltipButton: React.FC<{
  icon: React.ReactNode;
  label: string;
  className?: string;
}> = ({ icon, label, className }) => (
  <Tooltip>
    <TooltipTrigger asChild>
      <Button
        variant="ghost"
        size="icon"
        className={`bg-transparent inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors
          focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50
          [&_svg]:h-8 [&_svg]:w-8 h-12 w-12 text-zinc-300 hover:text-white hover:bg-zinc-800/30 ${className || ""}`}
      >
        {icon}
      </Button>
    </TooltipTrigger>
    <TooltipContent>
      <span>{label}</span>
    </TooltipContent>
  </Tooltip>
);

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
      <div className="min-h-screen bg-[#0F1923] text-white font-sans flex items-center justify-center">
        <div className="text-xl">Loading party details...</div>
      </div>
    )
  }

  if (!party) {
    return (
      <div className="min-h-screen bg-[#0F1923] text-white font-sans flex items-center justify-center">
        <div className="text-xl">Party not found.</div>
      </div>
    )
  }

  return (
    <TooltipProvider>
      <div className="min-h-screen bg-[#0F1923] text-white font-sans">
        <div className="container mx-auto py-8">
          <div className="max-w-5xl mx-auto">
            <div className="mb-8 flex justify-between items-center">
              <h1 className="text-3xl font-bold">{party.title || "Valorant Party"}</h1>
              <div className="flex gap-2">
                {user?.id === party.userId ? (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="destructive"
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
                        Delete Party
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Delete this party</TooltipContent>
                  </Tooltip>
                ) : (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="outline"
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
                        <LogOut className="h-4 w-4" />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Leave group</TooltipContent>
                  </Tooltip>
                )}
              </div>
            </div>

            {/* Party details badges section with IconTooltipButton */}
            <div className="flex flex-wrap items-center justify-start space-x-4 py-2 bg-zinc-900/30 px-4 rounded-lg mb-8">
              {/* Match Type Badge */}
              <IconTooltipButton 
                icon={<Trophy />} 
                label={formatDisplayText(party?.matchType)}
              />
              
              {/* Game Mode Badge */}
              <IconTooltipButton 
                icon={<GamepadIcon />} 
                label={formatDisplayText(party?.gameMode)}
              />
              
              {/* Team Size Badge */}
              <IconTooltipButton 
                icon={<Users />} 
                label={formatDisplayText(party?.teamSize)}
              />
              
              {/* Voice Preference Badge */}
              <IconTooltipButton 
                icon={<Mic />} 
                label={formatDisplayText(party?.voicePreference)}
              />
              
              {/* Age Restriction Badge */}
              <IconTooltipButton 
                icon={<Calendar />} 
                label={formatDisplayText(party?.ageRestriction)}
              />
              
              {/* Required Rank Badge */}
              <IconTooltipButton 
                icon={<Award />} 
                label={formatDisplayText(party?.requirements?.rank)}
              />
              
              {/* Required Region Badge */}
              <IconTooltipButton 
                icon={<Globe />} 
                label={formatDisplayText(party?.requirements?.region)}
              />
              
              {/* Voice Chat Required Badge */}
              <IconTooltipButton 
                icon={<Mic />} 
                label={`Voice Chat: ${formatBooleanText(party?.requirements?.voiceChat)}`}
              />
            </div>

            {/* Player Slots Container */}
            <PlayerSlotsContainer
              participants={participants}
              maxPlayers={party?.maxPlayers || 5}
              leaderId={leaderId}
              userProfiles={userProfiles}
              currentUser={user ? { id: user.id, avatar: user.avatar } : undefined}
              placeholderAvatar={placeholderAvatar}
              className="mt-8"
              onInviteClick={() => {
                // Implement invite functionality here if needed
                console.log("Invite player clicked");
              }}
            />
          </div>
        </div>
      </div>
    </TooltipProvider>
  )
}

