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
import { PlayerSlotsContainer } from "@/components/PlayerSlotsContainer"

// Gradient Badge Wrapper component to reduce repetition
const GradientBadge: React.FC<React.PropsWithChildren<{
  icon: React.ReactNode;
  label: string;
  className?: string;
}>> = ({ icon, label, className }) => (
  <div className="group relative">
    <div className="absolute -inset-0.5 bg-gradient-to-r from-[#FF4655]/30 to-[#FF4655]/10 rounded-full opacity-75 group-hover:opacity-100 blur-sm transition duration-300"></div>
    <Badge 
      variant="valorant" 
      size="xl"
      icon={icon}
      className={`relative w-full shadow-lg transition-all duration-300 inline-flex items-center justify-center gap-2 ${className || ""}`}
    >
      <span className="font-medium">{label}</span>
    </Badge>
  </div>
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
      <div className="min-h-screen bg-[#0F1923] text-white font-sans p-6">
        <div className="max-w-6xl mx-auto">
          <div className="mb-8">
            <div className="flex flex-col md:flex-row md:items-center justify-between mb-6">
              <h1 className="text-3xl font-bold text-white/90">
                Party Details
              </h1>
              <div className="flex items-center gap-3 mt-4 md:mt-0">
                {user?.id === leaderId ? (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="destructive" size="sm" onClick={() => {
                        if (window.confirm("Are you sure you want to delete this party?")) {
                          deleteParty(party.id)
                            .then(() => {
                              navigate("/dashboard/valorant")
                            })
                            .catch((err: any) => {
                              console.error("Error deleting party:", err)
                              alert("Failed to delete party")
                            })
                        }
                      }}>
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

            {/* Party details badges section - Simplified with GradientBadge component */}
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 mb-8">
              {/* Match Type Badge */}
              <GradientBadge 
                icon={<Trophy className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.matchType)}
              />
              
              {/* Game Mode Badge */}
              <GradientBadge 
                icon={<GamepadIcon className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.gameMode)}
              />
              
              {/* Team Size Badge */}
              <GradientBadge 
                icon={<Users className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.teamSize)}
              />
              
              {/* Voice Preference Badge */}
              <GradientBadge 
                icon={<Mic className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.voicePreference)}
              />
              
              {/* Age Restriction Badge */}
              <GradientBadge 
                icon={null} 
                label={formatDisplayText(party?.ageRestriction)}
              />
              
              {/* Required Rank Badge */}
              <GradientBadge 
                icon={<Award className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.requirements?.rank)}
              />
              
              {/* Required Region Badge */}
              <GradientBadge 
                icon={<Globe className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={formatDisplayText(party?.requirements?.region)}
              />
              
              {/* Voice Chat Required Badge */}
              <GradientBadge 
                icon={<Mic className="h-4 w-4 text-[#FF4655] group-hover:text-[#FF4655]/90 transition-colors" />} 
                label={`Voice Chat: ${formatBooleanText(party?.requirements?.voiceChat)}`}
              />
            </div>

            {/* Player Slots Container */}
            <PlayerSlotsContainer
              participants={participants}
              maxPlayers={party?.maxPlayers || 5}
              leaderId={leaderId}
              userProfiles={userProfiles}
              currentUser={user || undefined}
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

