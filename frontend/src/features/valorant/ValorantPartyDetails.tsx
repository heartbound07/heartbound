"use client"

import * as React from "react"
import { Users, LogOut, GamepadIcon, Trophy, Globe, Mic, Award, Calendar, Trash2, UserPlus, Loader2, X, Link2, Lock, Plus, Check, XIcon } from "lucide-react"
import { Button } from "@/components/ui/valorant/buttonparty"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"
import { useAuth } from "@/contexts/auth/useAuth"
import { useNavigate, useParams } from "react-router-dom"
import { deleteParty, getParty, leaveParty, joinParty, kickUserFromParty, inviteUserToParty, acceptJoinRequest, rejectJoinRequest } from "@/contexts/valorant/partyService"
import { usePartyUpdates } from "@/contexts/PartyUpdates"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { PlayerSlotsContainer } from "@/components/PlayerSlotsContainer"
import { formatDisplayText, formatBooleanText } from "@/utils/formatters"
import { CountdownTimer } from "@/components/CountdownTimer"
import { UserProfileModal } from "@/components/UserProfileModal"
import { SkeletonPartyDetails } from '@/components/ui/SkeletonUI'
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"

// Custom Toast Component
const Toast = ({ 
  message, 
  type = 'info', 
  onClose 
}: { 
  message: string; 
  type?: 'success' | 'error' | 'info'; 
  onClose: () => void;
}) => {
  const bgColor = type === 'success' 
    ? 'bg-green-500' 
    : type === 'error' 
      ? 'bg-[#FF4655]' 
      : 'bg-blue-500';

  return (
    <div className={`fixed top-4 right-4 z-50 flex items-center ${bgColor} text-white px-4 py-3 rounded-md shadow-lg`}>
      <span>{message}</span>
      <button
        onClick={onClose}
        className="ml-4 text-white hover:text-white/80 focus:outline-none"
      >
        <X size={18} />
      </button>
    </div>
  );
};

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
    <div className="bg-[#1F2731] rounded-lg px-3 py-2 flex items-center gap-2.5 transition-all duration-300 
      hover:bg-[#2C3A47] group h-12 w-full shadow-sm hover:shadow-md">
      <div className="text-[#8B97A4] group-hover:text-[#FF4655] transition-colors flex-shrink-0">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="text-xs text-[#8B97A4] font-medium mb-0.5">{label}</div>
        <div className="text-sm text-white font-medium truncate">{value}</div>
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
            className="text-[#8B97A4] group-hover:text-[#FF4655]" 
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
        className="bg-[#1F2731] border border-white/10 z-[100]"
        style={{ transform: "translateZ(0)" }}
      >
        <div>
          <div className="font-medium">{label}</div>
          <div className="text-[#8B97A4]">{value}</div>
        </div>
      </TooltipContent>
    </Tooltip>
  );
};

export default function ValorantPartyDetails() {
  const { user, hasRole } = useAuth()
  const navigate = useNavigate()
  const { partyId } = useParams<{ partyId: string }>()
  const { update, userActiveParty, setUserActiveParty } = usePartyUpdates()

  const [party, setParty] = React.useState<any>(null)
  const [userProfiles, setUserProfiles] = React.useState<Record<string, UserProfileDTO>>({})
  const [isLoading, setIsLoading] = React.useState(true)
  const [leaderId, setLeaderId] = React.useState<string>("")
  const [participants, setParticipants] = React.useState<string[]>([])
  const [isJoining, setIsJoining] = React.useState(false)
  const [selectedProfileId, setSelectedProfileId] = React.useState<string | null>(null)
  const [profilePosition, setProfilePosition] = React.useState<{ x: number, y: number } | null>(null)
  const [invitedUsers, setInvitedUsers] = React.useState<string[]>([])
  const [isInvited, setIsInvited] = React.useState(false)
  const [joinRequestProfiles, setJoinRequestProfiles] = React.useState<Record<string, UserProfileDTO>>({})
  
  // Toast state
  const [toastInfo, setToastInfo] = React.useState<{
    visible: boolean;
    message: string;
    type: 'success' | 'error' | 'info';
  }>({
    visible: false,
    message: '',
    type: 'info'
  });

  // Function to show a toast message
  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'info') => {
    setToastInfo({
      visible: true,
      message,
      type
    });
    
    // Auto-hide toast after 3 seconds
    setTimeout(() => {
      setToastInfo(prev => ({ ...prev, visible: false }));
    }, 3000);
  };

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
        if (updateObj?.eventType && ["PARTY_JOINED", "PARTY_UPDATED", "PARTY_LEFT", "PARTY_USER_KICKED"].includes(updateObj.eventType)) {
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

  // Add effect to load user profiles for join requests
  React.useEffect(() => {
    if (party?.joinRequests && party.joinRequests.length > 0) {
      // Fetch profiles for users who have requested to join
      const fetchJoinRequestProfiles = async () => {
        try {
          const profiles = await getUserProfiles(party.joinRequests);
          setJoinRequestProfiles(profiles);
        } catch (error) {
          console.error("Error fetching join request profiles:", error);
        }
      };
      
      fetchJoinRequestProfiles();
    }
  }, [party?.joinRequests]);

  // Add debug log before calculating participants details
  console.debug("Party data:", party);
  console.debug("Participants raw:", party?.participants);

  // Handle party deletion
  const handleDeleteParty = async () => {
    if (window.confirm("Are you sure you want to delete this party? This action cannot be undone.")) {
      try {
        await deleteParty(party.id)
        
        // Immediately reset the active party state
        setUserActiveParty(null)
        
        showToast("Party successfully deleted", "success")
        navigate("/dashboard/valorant")
      } catch (err: any) {
        console.error("Error deleting party:", err)
        showToast(err.response?.data?.message || "Failed to delete party", "error")
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
          // Reset active party state here too
          setUserActiveParty(null)
          // Navigate away after deletion
          navigate("/dashboard/valorant");
        })
        .catch((err) => {
          console.error("Error auto-deleting expired party:", err);
        });
    }
  };

  // Inside the component, add a new handler for joining the party (modified to use custom toast)
  const handleJoinParty = () => {
    if (party?.requirements?.inviteOnly && !isInvited) {
      showToast("This party is invite-only. You need an invitation to join.", "error");
      return;
    }
    
    setIsJoining(true);
    joinParty(party.id)
      .then(() => {
        showToast("You have joined the party.", "success");
        // The WebSocket update will handle updating the UI
      })
      .catch((err: any) => {
        console.error("Error joining party:", err);
        showToast(err.message || "Could not join the party. Try again later.", "error");
      })
      .finally(() => {
        setIsJoining(false);
      });
  };

  // Add this function inside the ValorantPartyDetails component
  const handleCopyLink = () => {
    const currentUrl = window.location.href;
    navigator.clipboard.writeText(currentUrl)
      .then(() => {
        showToast("Party link copied to clipboard!", "success");
      })
      .catch((err) => {
        console.error("Failed to copy link:", err);
        showToast("Failed to copy link to clipboard", "error");
      });
  };

  const handleProfileView = (userId: string, position: { x: number, y: number }) => {
    setSelectedProfileId(userId);
    setProfilePosition(position);
  };
  
  const handleCloseProfileModal = () => {
    setSelectedProfileId(null);
    setProfilePosition(null);
  };

  // Add a function to determine if the current user has kick permissions
  const hasKickPermission = React.useMemo(() => {
    // Party leader always has kick permission
    const isUserLeader = user && leaderId === user.id;
    
    // Admin or Moderator also has kick permission
    const hasAdminRole = hasRole && (
      hasRole('ADMIN') || hasRole('MODERATOR')
    );
    
    return isUserLeader || hasAdminRole;
  }, [user, leaderId, hasRole]);
  
  // Add a function to determine if a user has admin or moderator role
  const hasAdminOrModeratorRole = (userId: string): boolean => {
    const userProfile = userProfiles[userId];
    // Check if profile exists and has roles array
    if (userProfile && userProfile.roles) {
      // Check if user has ADMIN or MODERATOR role
      return userProfile.roles.some(role => role === 'ADMIN' || role === 'MODERATOR');
    }
    return false;
  };

  // Modified handleKickUser function with additional checks
  const handleKickUser = async (userId: string) => {
    if (!party) return;
    
    try {
      // Check if the current user is the party leader (not admin/mod) and target is admin/mod
      const isUserLeader = user && leaderId === user.id;
      const isCurrentUserAdmin = hasRole && (hasRole('ADMIN') || hasRole('MODERATOR'));
      const isTargetUserAdminOrMod = hasAdminOrModeratorRole(userId);
      
      // If leader trying to kick admin/mod - prevent action
      if (isUserLeader && !isCurrentUserAdmin && isTargetUserAdminOrMod) {
        showToast(
          "You cannot kick administrators or moderators from the party.", 
          "error"
        );
        return;
      }
      
      // Proceed with kick as usual
      await kickUserFromParty(party.id, userId);
      // Toast notification
      showToast(`Player has been kicked from the party.`, "success");
      // No need to update state here as the WebSocket update will handle it
    } catch (err: any) {
      console.error("Error kicking user:", err);
      showToast(err.message || "Could not kick the user. Try again later.", "error");
    }
  };

  // Move this declaration up before it's used in canDeleteParty
  const isUserLeader = leaderId === user?.id;
  const isUserParticipant = participants.includes(user?.id || '');

  // Add a new variable to check if user has admin or moderator privileges
  const hasAdminPrivileges = React.useMemo(() => {
    return hasRole && (hasRole('ADMIN') || hasRole('MODERATOR'));
  }, [hasRole]);

  // Determine who can delete the party (leader or admin/mod)
  const canDeleteParty = isUserLeader || hasAdminPrivileges;

  // Add effect to set userActiveParty when this component loads
  React.useEffect(() => {
    if (party?.id && user?.id) {
      const isCreator = party.userId === user.id;
      const isParticipant = party.participants?.includes(user.id);
      
      if (isCreator || isParticipant) {
        // This is the user's active party - we'd ideally call a method to 
        // set this in the context, but for simplicity we rely on WebSocket events
      }
    }
  }, [party?.id, user?.id, party?.userId, party?.participants]);

  // Assuming you have a handleLeaveParty function, update it:
  const handleLeaveParty = async () => {
    try {
      await leaveParty(party.id);
      
      // Immediately reset the active party state
      setUserActiveParty(null);
      
      showToast("You have left the party", "success");
      navigate("/dashboard/valorant");
    } catch (err: any) {
      console.error("Error leaving party:", err);
      showToast(err.response?.data?.message || "Failed to leave party", "error");
    }
  };

  // Add a new function to invite users
  const handleInviteUser = async (userId: string) => {
    try {
      await inviteUserToParty(party.id, userId);
      showToast("Invitation sent successfully", "success");
    } catch (err: any) {
      console.error("Error inviting user:", err);
      showToast(err.message || "Could not send invitation", "error");
    }
  };

  // Check if current user is waiting for a join request approval
  const isWaitingForApproval = user?.id && party?.joinRequests?.includes(user.id);

  // Add functions to handle accepting and rejecting join requests
  const handleAcceptJoinRequest = async (userId: string) => {
    try {
      await acceptJoinRequest(party.id, userId);
      showToast("User has been added to the party", "success");
      
      // Refresh party data to update the UI
      if (party?.id) {
        const updatedParty = await getParty(party.id);
        setParty(updatedParty);
      }
    } catch (err: any) {
      console.error("Error accepting join request:", err);
      showToast(err.message || "Could not accept join request", "error");
    }
  };
  
  const handleRejectJoinRequest = async (userId: string) => {
    try {
      await rejectJoinRequest(party.id, userId);
      showToast("Join request rejected", "success");
      
      // Refresh party data to update the UI
      if (party?.id) {
        const updatedParty = await getParty(party.id);
        setParty(updatedParty);
      }
    } catch (err: any) {
      console.error("Error rejecting join request:", err);
      showToast(err.message || "Could not reject join request", "error");
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#0F1923] text-white pb-8 pt-20">
        <div className="container max-w-screen-xl mx-auto px-4">
          <SkeletonPartyDetails theme="valorant" />
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

  return (
    <TooltipProvider>
      {/* Render the toast if visible */}
      {toastInfo.visible && (
        <Toast 
          message={toastInfo.message} 
          type={toastInfo.type} 
          onClose={() => setToastInfo(prev => ({ ...prev, visible: false }))} 
        />
      )}
      
      <div className="min-h-screen bg-[#0F1923] text-white font-sans flex flex-col p-6">
        <div className="fixed inset-0 bg-[#0F1923] z-0">
          <div className="absolute inset-0 bg-gradient-to-br from-[#FF4655]/10 to-transparent opacity-50"></div>
          <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-[#1F2731] to-transparent opacity-30"></div>
        </div>
        
        <div className="max-w-5xl mx-auto w-full space-y-8 relative z-10">
          {/* Combined Party Details and Game Settings Container */}
          <div className="bg-[#1F2731]/60 backdrop-blur-sm rounded-xl border border-white/5 shadow-2xl overflow-hidden">
            {/* Party Header Section */}
            <div className="p-6 border-b border-white/10">
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-3 mb-2">
                    <h1 className="text-3xl font-bold text-white mr-2 truncate">
                      {party.title || "Unnamed Party"}
                    </h1>
                    <div className="flex flex-wrap items-center gap-2">
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
                        icon={<Lock className="h-5 w-5" />} 
                        label="Access" 
                        value={party?.requirements?.inviteOnly ? "Invite Only" : "Open"} 
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
                  {/* Game and Status Info Row */}
                  <div className="flex gap-2 flex-wrap mt-2">
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
                    {party.expiresAt && !isPartyFull && (
                      <div className="py-1 px-3 rounded-full bg-[#1F2731] text-xs font-semibold border border-white/10">
                        <CountdownTimer 
                          expiresAt={party.expiresAt} 
                          onExpire={handlePartyExpire}
                        />
                      </div>
                    )}
                  </div>
                </div>
                
                <div className="flex flex-wrap gap-2">
                  {/* Copy Link Button - shown to leaders and participants */}
                  {(isUserLeader || isUserParticipant) && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-[#283A4B] hover:bg-[#2A3F56] text-white border border-white/10 hover:border-white/30 shadow-md hover:shadow-lg transition-all duration-300 rounded-full"
                          size="sm"
                          onClick={handleCopyLink}
                        >
                          <Link2 className="h-4 w-4 text-[#8B97A4] group-hover:text-white transition-colors" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent sideOffset={8} className="bg-[#283A4B] border border-white/10 z-[100]">
                        <p className="text-sm text-white">Copy party link</p>
                      </TooltipContent>
                    </Tooltip>
                  )}
                  
                  {/* Delete Party Button - shown to party leader OR admin/moderator */}
                  {canDeleteParty && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-[#283A4B] hover:bg-[#2A3F56] text-white border border-white/10 hover:border-white/30 shadow-md hover:shadow-lg transition-all duration-300 rounded-full"
                          size="sm"
                          onClick={handleDeleteParty}
                        >
                          <Trash2 className="h-4 w-4 text-[#8B97A4] group-hover:text-white transition-colors" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent sideOffset={8} className="bg-[#283A4B] border border-white/10 z-[100]">
                        <p className="text-sm text-white">Delete this party?</p>
                      </TooltipContent>
                    </Tooltip>
                  )}
                  
                  {/* Leave Party Button - shown to participants (including admin/mod who aren't the leader) */}
                  {isUserParticipant && !isUserLeader && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-[#283A4B] hover:bg-[#2A3F56] text-white border border-white/10 hover:border-white/30 shadow-md hover:shadow-lg transition-all duration-300 rounded-full"
                          size="sm"
                          onClick={handleLeaveParty}
                        >
                          <LogOut className="h-4 w-4 text-[#8B97A4] group-hover:text-white transition-colors" />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent sideOffset={8} className="bg-[#283A4B] border border-white/10 z-[100]">
                        <p className="text-sm text-white">Leave this party?</p>
                      </TooltipContent>
                    </Tooltip>
                  )}
                  
                  {/* Join Party Button - shown to non-participants */}
                  {!isUserParticipant && (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          className="bg-[#283A4B] hover:bg-[#2A3F56] text-white border border-white/10 hover:border-white/30 shadow-md hover:shadow-lg transition-all duration-300 rounded-full"
                          size="sm"
                          onClick={handleJoinParty}
                          disabled={isJoining || isPartyFull || (party?.requirements?.inviteOnly && !isInvited && !isWaitingForApproval)}
                        >
                          {isJoining ? (
                            <>
                              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                              Joining...
                            </>
                          ) : isWaitingForApproval ? (
                            <>
                              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                              Waiting...
                            </>
                          ) : party?.requirements?.inviteOnly && !isInvited ? (
                            <>
                              <Lock className="h-4 w-4 mr-2" />
                              Invite Only
                            </>
                          ) : isPartyFull ? (
                            <>
                              <Users className="h-4 w-4 mr-2" />
                              Party Full
                            </>
                          ) : (
                            <>
                              <Plus className="h-4 w-4 mr-2" />
                              Join Party
                            </>
                          )}
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent side="bottom">
                        {isWaitingForApproval 
                          ? "Your join request is pending approval from the party leader."
                          : party?.requirements?.inviteOnly && !isInvited 
                            ? "This party is invite-only. You need an invitation to join."
                            : isPartyFull 
                              ? "This party is currently full." 
                              : "Join this party"}
                      </TooltipContent>
                    </Tooltip>
                  )}
                </div>
              </div>
              {party.description && (
                <p className="text-[#8B97A4] mt-3 sm:mt-4 max-w-3xl">
                  {party.description}
                </p>
              )}
            </div>
            
            {/* Combined Game Settings and Requirements */}
            <div className="p-6 bg-[#1F2731]/40">
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
          
          {/* Player slots container with updated props */}
          <PlayerSlotsContainer 
            participants={participants}
            maxPlayers={party?.maxPlayers || 5}
            leaderId={leaderId}
            userProfiles={userProfiles}
            currentUser={user ? { id: user.id, avatar: user.avatar } : undefined}
            placeholderAvatar={placeholderAvatar}
            className="bg-[#1F2731]/60 backdrop-blur-sm border border-white/5 shadow-xl"
            onInviteClick={() => console.log("Invite player clicked")}
            onProfileView={handleProfileView}
            onKickUser={handleKickUser}
            hasKickPermission={hasKickPermission}
          />
          
          {/* Add the profile modal component */}
          <UserProfileModal
            isOpen={!!selectedProfileId}
            onClose={handleCloseProfileModal}
            userProfile={selectedProfileId ? userProfiles[selectedProfileId] : null}
            position={profilePosition}
          />

          {/* Add this section to render the invite UI for party leaders */}
          {(isUserLeader || hasAdminPrivileges) && party?.requirements?.inviteOnly && (
            <div className="mt-6 bg-[#1F2731]/60 backdrop-blur-sm rounded-xl border border-white/5 shadow-md p-6">
              <h3 className="text-lg font-semibold mb-4">Invite Users</h3>
              <div className="space-y-4">
                <p className="text-sm text-white/70">
                  This is an invite-only party. As the leader, you can invite users to join.
                </p>
                
                {/* Join Requests Section */}
                {party.joinRequests && party.joinRequests.length > 0 && (
                  <div className="mt-4">
                    <h4 className="text-sm font-medium mb-2 text-[#FF4655]">Join Requests:</h4>
                    <div className="flex flex-wrap gap-2">
                      {party.joinRequests.map((userId: string) => (
                        <div key={userId} className="flex items-center gap-2 bg-[#283A4B] p-2 rounded-md">
                          <Avatar className="h-6 w-6">
                            <AvatarImage 
                              src={joinRequestProfiles[userId]?.avatar || placeholderAvatar} 
                              alt={joinRequestProfiles[userId]?.username || "User"} 
                            />
                            <AvatarFallback>
                              {joinRequestProfiles[userId]?.username?.charAt(0) || "U"}
                            </AvatarFallback>
                          </Avatar>
                          <span className="text-sm">{joinRequestProfiles[userId]?.username || "Unknown User"}</span>
                          
                          {/* Accept/Reject buttons */}
                          <div className="flex gap-1 ml-2">
                            <Button 
                              onClick={() => handleAcceptJoinRequest(userId)}
                              className="bg-green-500 hover:bg-green-600 p-1 h-7 w-7 rounded-full"
                            >
                              <Check className="h-3 w-3" />
                            </Button>
                            <Button 
                              onClick={() => handleRejectJoinRequest(userId)}
                              className="bg-red-500 hover:bg-red-600 p-1 h-7 w-7 rounded-full"
                            >
                              <XIcon className="h-3 w-3" />
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                
                {/* Invited Users Section (existing code) */}
                {invitedUsers.length > 0 && (
                  <div className="mt-4">
                    <h4 className="text-sm font-medium mb-2">Invited Users:</h4>
                    <div className="flex flex-wrap gap-2">
                      {invitedUsers.map(userId => (
                        <div key={userId} className="flex items-center gap-2 bg-[#283A4B] p-2 rounded-md">
                          <Avatar className="h-6 w-6">
                            <AvatarImage 
                              src={userProfiles[userId]?.avatar || placeholderAvatar} 
                              alt={userProfiles[userId]?.username || "User"} 
                            />
                            <AvatarFallback>
                              {userProfiles[userId]?.username?.charAt(0) || "U"}
                            </AvatarFallback>
                          </Avatar>
                          <span className="text-sm">{userProfiles[userId]?.username || "Unknown User"}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </TooltipProvider>
  )
}

