"use client"

import * as React from "react"
import { Users, LogOut, GamepadIcon, Trophy, Globe, Mic, Award, Calendar, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate, useParams } from "react-router-dom"
import { deleteParty, getParty, leaveParty } from "@/contexts/valorant/partyService"
import { usePartyUpdates } from "@/contexts/PartyUpdates"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { PlayerSlotsContainer } from "@/components/PlayerSlotsContainer"
import { formatDisplayText, formatBooleanText } from "@/utils/formatters"
import { CountdownTimer } from "@/components/CountdownTimer"

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
    <div className="bg-zinc-800/70 rounded-lg px-3 py-2 flex items-center gap-2.5 transition-all duration-300 
      hover:bg-zinc-700/50 group h-12 w-full shadow-sm hover:shadow-md">
      <div className="text-zinc-400 group-hover:text-[#FF4655] transition-colors flex-shrink-0">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-xs text-zinc-400 truncate">{label}</div>
        <div className="text-sm font-medium text-white truncate">{value}</div>
      </div>
    </div>
  );
};

// Create a new IconBadge component for simplified icon-only display with optimized animations
const IconBadge = ({ 
  icon, 
  label, 
  value 
}: { 
  icon: React.ReactNode; 
  label: string; 
  value: string;
}) => {
  return (
    <Tooltip delayDuration={100}>
      <TooltipTrigger asChild>
        <div 
          className="p-2.5 rounded-full bg-transparent hover:bg-white/5 cursor-pointer group"
          style={{
            willChange: "background-color, transform",
            transition: "background-color 150ms ease, transform 200ms ease",
            transform: "translateZ(0)"
          }}
          onMouseEnter={(e) => {
            const target = e.currentTarget;
            target.style.transform = "translateZ(0) scale(1.05)";
          }}
          onMouseLeave={(e) => {
            const target = e.currentTarget;
            target.style.transform = "translateZ(0) scale(1)";
          }}
        >
          <div 
            className="text-zinc-400 group-hover:text-[#FF4655]" 
            style={{ 
              transition: "color 150ms ease",
              contain: "layout style"
            }}
          >
            {icon}
          </div>
        </div>
      </TooltipTrigger>
      <TooltipContent 
        sideOffset={5} 
        className="bg-zinc-900 border border-white/10"
        style={{ transform: "translateZ(0)" }}
      >
        <div>
          <div className="font-medium">{label}</div>
          <div className="text-zinc-300">{value}</div>
        </div>
      </TooltipContent>
    </Tooltip>
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
  const [leaderId, setLeaderId] = React.useState<string>("")
  const [participants, setParticipants] = React.useState<string[]>([])

  // Placeholder avatar for participants who don't have an available avatar.
  const placeholderAvatar = "https://v0.dev/placeholder.svg?height=400&width=400"

  // Initial party fetch
  React.useEffect(() => {
    if (partyId) {
      setIsLoading(true)
      getParty(partyId)
        .then((data) => {
          setParty(data)
          setLeaderId(data.userId)
          setParticipants(data.participants || [])
          
          // Ensure participants is handled as an array regardless of how it's received
          const participantsData = data.participants || [];
          // Add explicit type assertion and filter to ensure we only pass strings
          const userIdsToFetch = (Array.isArray(participantsData) 
            ? participantsData 
            : (typeof participantsData === 'object' ? Object.values(participantsData) : []))
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
              setLeaderId(data.userId)
              setParticipants(data.participants || [])
              
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

  // Handle party deletion
  const handleDeleteParty = async () => {
    if (window.confirm("Are you sure you want to delete this party? This action cannot be undone.")) {
      try {
        await deleteParty(party.id)
        navigate("/dashboard/valorant")
      } catch (err: any) {
        console.error("Error deleting party:", err)
        alert("Failed to delete party")
      }
    }
  }

  // Check if party is full
  const isPartyFull = party && party.participants && party.maxPlayers 
    ? party.participants.length >= party.maxPlayers 
    : false;

  // Handle party expiration
  const handlePartyExpire = () => {
    // Only allow party leader to auto-delete
    if (leaderId === user?.id && party) {
      console.log("Party expired, auto-deleting...");
      deleteParty(party.id)
        .then(() => {
          // Navigate away after deletion
          navigate("/dashboard/valorant");
        })
        .catch((err) => {
          console.error("Error auto-deleting expired party:", err);
        });
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans flex items-center justify-center">
        <div className="p-8 rounded-xl bg-zinc-900/50 backdrop-blur-sm border border-white/5 shadow-lg flex flex-col items-center">
          <div className="w-12 h-12 rounded-full border-2 border-t-[#FF4655] border-r-[#FF4655]/50 border-b-[#FF4655]/20 border-l-transparent animate-spin mb-4"></div>
          <div className="text-xl font-medium text-white/90">Loading party details...</div>
          <div className="text-sm text-white/50 mt-2">Please wait while we retrieve the party information.</div>
        </div>
      </div>
    )
  }

  if (!party) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans flex items-center justify-center">
        <div className="p-8 rounded-xl bg-zinc-900/50 backdrop-blur-sm border border-white/5 shadow-lg text-center">
          <div className="text-5xl font-bold text-[#FF4655] mb-4">404</div>
          <div className="text-xl font-medium text-white/90 mb-2">Party Not Found</div>
          <div className="text-sm text-white/50 mb-6">The party you are looking for does not exist or has been deleted.</div>
          <Button 
            onClick={() => navigate("/dashboard/valorant")} 
            className="bg-[#FF4655] hover:bg-[#FF4655]/90 text-white font-medium px-4 py-2 rounded-md transition-colors"
          >
            Return to Dashboard
          </Button>
        </div>
      </div>
    )
  }

  // Check if current user is a participant of the party
  const isUserParticipant = participants.includes(user?.id || '');
  const isUserLeader = leaderId === user?.id;
  
  return (
    <TooltipProvider>
      <div className="min-h-screen bg-gradient-to-br from-[#0F1923] to-[#1A242F] text-white font-sans flex flex-col p-6">
        <div className="max-w-7xl mx-auto w-full space-y-8">
          {/* Combined Party Details and Game Settings Container */}
          <div className="bg-gradient-to-br from-zinc-900/90 to-zinc-900/60 backdrop-blur-md rounded-xl overflow-hidden border border-white/5 shadow-2xl">
            {/* Party Header Section */}
            <div className="p-6 border-b border-white/5">
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-3 mb-2">
                    <h1 className="text-3xl font-bold text-white mr-2 truncate">
                      {party.title || "Unnamed Party"}
                    </h1>
                    <div className="flex gap-2 flex-wrap">
                      <span className="py-1 px-3 rounded-full bg-[#FF4655]/10 text-[#FF4655] text-xs font-semibold border border-[#FF4655]/20">
                        {formatDisplayText(party.game)}
                      </span>
                      <span className={`py-1 px-3 rounded-full text-xs font-semibold ${
                        party.status === 'open' 
                        ? 'bg-green-500/10 text-green-400 border border-green-500/20' 
                        : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                      }`}>
                        {formatDisplayText(party.status)}
                      </span>
                      
                      {/* Only show countdown timer when party is not full */}
                      {party.expiresAt && !isPartyFull && (
                        <div className="py-1 px-3 rounded-full bg-zinc-800/80 text-xs font-semibold border border-white/10">
                          <CountdownTimer 
                            expiresAt={party.expiresAt} 
                            onExpire={handlePartyExpire}
                          />
                        </div>
                      )}
                    </div>
                  </div>
                </div>
                
                <div className="flex flex-wrap gap-2">
                  {isUserLeader ? (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-zinc-800/70 hover:bg-zinc-700/90 text-white border border-white/10 hover:border-white/30
                          shadow-md hover:shadow-lg transition-all duration-300 rounded-lg"
                          size="sm"
                          onClick={handleDeleteParty}
                        >
                          <span className="flex items-center gap-2">
                            <Trash2 className="h-4 w-4 text-zinc-400 group-hover:text-white transition-colors" />
                            <span className="font-medium">Delete Party</span>
                          </span>
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className="text-sm text-white">Delete this party permanently</p>
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
            
            {/* Combined Game Settings and Requirements */}
            <div className="p-6 bg-zinc-900/40">
              {/* Party Requirements Icon Badges (now first) */}
              <div className="mb-4">
                <div 
                  className="flex flex-wrap items-center justify-center sm:justify-start gap-5"
                  style={{ containIntrinsicSize: "0 80px", content: "layout" }}
                >
                  <IconBadge 
                    icon={<Award className="h-5 w-5" />} 
                    label="Rank" 
                    value={formatDisplayText(party?.requirements?.rank)} 
                  />
                  <IconBadge 
                    icon={<Globe className="h-5 w-5" />} 
                    label="Region" 
                    value={formatDisplayText(party?.requirements?.region)} 
                  />
                  <IconBadge 
                    icon={<Mic className="h-5 w-5" />} 
                    label="Voice Chat" 
                    value={formatBooleanText(party?.requirements?.voiceChat)} 
                  />
                  <IconBadge 
                    icon={<Calendar className="h-5 w-5" />} 
                    label="Age" 
                    value={formatDisplayText(party?.ageRestriction)} 
                  />
                  <IconBadge 
                    icon={<Mic className="h-5 w-5" />} 
                    label="Voice Preference" 
                    value={formatDisplayText(party?.voicePreference)} 
                  />
                </div>
              </div>
              
              {/* Game Settings Detail Badges (now second) */}
              <div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <DetailBadge 
                    icon={<Trophy className="h-4 w-4" />} 
                    label="Match Type" 
                    value={formatDisplayText(party?.matchType)} 
                  />
                  <DetailBadge 
                    icon={<GamepadIcon className="h-4 w-4" />} 
                    label="Game Mode" 
                    value={formatDisplayText(party?.gameMode)} 
                  />
                  <DetailBadge 
                    icon={<Users className="h-4 w-4" />} 
                    label="Team Size" 
                    value={formatDisplayText(party?.teamSize)} 
                  />
                </div>
              </div>
            </div>
          </div>
          
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
    </TooltipProvider>
  )
}

