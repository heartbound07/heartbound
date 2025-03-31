import { useState, useEffect, useCallback } from 'react';
import { getUserProfiles, type UserProfileDTO } from "@/config/userService";
import { acceptJoinRequest, rejectJoinRequest, getParty } from "@/contexts/valorant/partyService";
import { usePartyUpdates, type LFGPartyEvent } from "@/contexts/PartyUpdates";

interface UsePartyJoinRequestsParams {
  partyId: string | null;
  userId: string | undefined;
  isUserLeader: boolean;
  party: any; // Replace with proper type when available
  updatePartyState: (updatedParty: any) => void;
  showToast: (message: string, type: 'success' | 'error' | 'info') => void;
}

export function usePartyJoinRequests({
  partyId,
  userId,
  isUserLeader,
  party,
  updatePartyState,
  showToast
}: UsePartyJoinRequestsParams) {
  // State for join request profiles
  const [joinRequestProfiles, setJoinRequestProfiles] = useState<Record<string, UserProfileDTO>>({});
  const [isProcessingRequest, setIsProcessingRequest] = useState(false);
  
  // Get party updates context
  const { update, clearUpdate } = usePartyUpdates();

  // Check if the current user has a pending join request
  const hasJoinRequested = userId ? party?.joinRequests?.includes(userId) : false;

  // Process websocket updates related to join requests
  useEffect(() => {
    if (!update || !party || update.party?.id !== party.id) return;

    const updatedParty = update.party;

    switch (update.eventType) {
      case 'PARTY_JOIN_REQUEST':
      case 'PARTY_JOIN_REQUESTED':
        // Show toast notification for party owner
        if (userId === party.userId) {
          showToast("A new player has requested to join your party", "info");
        }
        
        // Load profiles for new join requests if needed
        if (updatedParty.joinRequests?.length > 0) {
          const missingRequestProfiles = updatedParty.joinRequests.filter(
            (id: string) => !joinRequestProfiles[id]
          );
          
          if (missingRequestProfiles.length > 0) {
            getUserProfiles(missingRequestProfiles)
              .then(profiles => {
                setJoinRequestProfiles(prev => ({
                  ...prev,
                  ...profiles
                }));
              })
              .catch(error => {
                console.error("Error fetching join request profiles:", error);
              });
          }
        }
        break;
      
      case 'PARTY_JOIN_REQUEST_ACCEPTED':
        // Toast for the target user who was accepted
        if (userId === update.targetUserId) {
          showToast("Your request to join the party was accepted!", "success");
        }
        
        // If a join request was accepted and we have the profile in joinRequestProfiles,
        // remove it from joinRequestProfiles
        if (update.targetUserId && joinRequestProfiles[update.targetUserId]) {
          setJoinRequestProfiles(prev => {
            const newProfiles = { ...prev };
            delete newProfiles[update.targetUserId as string];
            return newProfiles;
          });
        }
        break;
      
      case 'PARTY_JOIN_REQUEST_REJECTED':
        // Toast for the target user who was rejected
        if (userId === update.targetUserId) {
          showToast("Your request to join the party was rejected", "error");
        }
        
        // Remove the rejected user from join request profiles if present
        if (update.targetUserId && joinRequestProfiles[update.targetUserId]) {
          setJoinRequestProfiles(prev => {
            const newProfiles = { ...prev };
            delete newProfiles[update.targetUserId as string];
            return newProfiles;
          });
        }
        break;
    }
  }, [update, party?.id, userId, joinRequestProfiles, showToast]);

  // Handle accepting join requests
  const handleAcceptJoinRequest = useCallback(async (requestUserId: string) => {
    if (!partyId) return;
    
    setIsProcessingRequest(true);
    try {
      await acceptJoinRequest(partyId, requestUserId);
      showToast("Join request accepted", "success");
      
      // Refresh party data to update the UI
      const updatedParty = await getParty(partyId);
      updatePartyState(updatedParty);
      
      // Remove the user from joinRequestProfiles after accepting
      setJoinRequestProfiles(prev => {
        const newProfiles = { ...prev };
        delete newProfiles[requestUserId];
        return newProfiles;
      });
    } catch (err: any) {
      console.error("Error accepting join request:", err);
      showToast(
        err.response?.data?.message || err.message || "Could not accept join request", 
        "error"
      );
    } finally {
      setIsProcessingRequest(false);
    }
  }, [partyId, showToast, updatePartyState]);

  // Handle rejecting join requests
  const handleRejectJoinRequest = useCallback(async (requestUserId: string) => {
    if (!partyId) return;
    
    setIsProcessingRequest(true);
    try {
      await rejectJoinRequest(partyId, requestUserId);
      showToast("Join request rejected", "success");
      
      // Refresh party data to update the UI
      const updatedParty = await getParty(partyId);
      updatePartyState(updatedParty);
      
      // Remove the user from joinRequestProfiles after rejecting
      setJoinRequestProfiles(prev => {
        const newProfiles = { ...prev };
        delete newProfiles[requestUserId];
        return newProfiles;
      });
    } catch (err: any) {
      console.error("Error rejecting join request:", err);
      showToast(
        err.response?.data?.message || err.message || "Could not reject join request", 
        "error"
      );
    } finally {
      setIsProcessingRequest(false);
    }
  }, [partyId, showToast, updatePartyState]);

  // Load initial join request profiles
  useEffect(() => {
    if (party?.joinRequests?.length > 0) {
      getUserProfiles(party.joinRequests)
        .then(profiles => {
          setJoinRequestProfiles(profiles);
        })
        .catch(error => {
          console.error("Error fetching initial join request profiles:", error);
        });
    }
  }, [party?.joinRequests]);

  return {
    joinRequestProfiles,
    hasJoinRequested,
    isProcessingRequest,
    handleAcceptJoinRequest,
    handleRejectJoinRequest
  };
} 