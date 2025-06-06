import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '@/contexts/auth/useAuth';
import type { LFGPartyEvent } from '../types/websocket';

interface PartyUpdatesHookReturn {
  update: LFGPartyEvent | null;
  error: string | null;
  clearUpdate: () => void;
  userActiveParty: string | null;
  setUserActiveParty: (partyId: string | null) => void;
  isConnected: boolean;
}

export const usePartyUpdates = (): PartyUpdatesHookReturn => {
  const { subscribe, lastError, isConnected } = useWebSocket();
  const { user } = useAuth();
  const [update, setUpdate] = useState<LFGPartyEvent | null>(null);
  const [userActiveParty, setUserActiveParty] = useState<string | null>(null);

  const clearUpdate = useCallback(() => {
    setUpdate(null);
  }, []);

  const error = lastError?.type === 'server' && lastError.message.includes('/topic/party') 
    ? lastError.message 
    : null;

  // Effect to update active party status when WebSocket sends an update
  useEffect(() => {
    // Early return if user is not authenticated or no update is received
    if (!user?.id || !update?.party) return;
    
    const party = update.party;
    const isCreator = party.userId === user.id;
    const isParticipant = party.participants?.some(
      (pid: string) => String(pid) === String(user.id)
    );
    const isSameParty = String(userActiveParty) === String(party.id);
    
    // Create a switch statement for better readability and performance
    switch(update.eventType) {
      case 'PARTY_CREATED':
        if (isCreator) setUserActiveParty(String(party.id));
        break;
      case 'PARTY_JOINED':
        if (isParticipant) setUserActiveParty(String(party.id));
        break;
      case 'PARTY_DELETED':
        if (isSameParty) setUserActiveParty(null);
        break;
      case 'PARTY_LEFT':
      case 'PARTY_USER_KICKED':
        if (isSameParty && !isParticipant) setUserActiveParty(null);
        break;
      case 'PARTY_JOIN_REQUEST_ACCEPTED':
        if (isParticipant) setUserActiveParty(String(party.id));
        break;
      // No need to change userActiveParty for these events, but we keep them to maintain functionality
      case 'PARTY_JOIN_REQUEST':
      case 'PARTY_JOIN_REQUESTED':
      case 'PARTY_JOIN_REQUEST_REJECTED':
        // Just maintain the existing state
        break;
    }
  }, [update?.eventType, update?.party, update?.targetUserId, user?.id, userActiveParty]);

  // Subscribe to party updates
  useEffect(() => {
    console.log('[usePartyUpdates] Setting up subscription to /topic/party');
    
    const unsubscribe = subscribe<LFGPartyEvent>('/topic/party', (message: LFGPartyEvent) => {
      console.info('[usePartyUpdates] Received update:', message);
      setUpdate(message);
    });

    return unsubscribe;
  }, [subscribe]);

  return useMemo(() => ({
    update,
    error,
    clearUpdate,
    userActiveParty,
    setUserActiveParty,
    isConnected,
  }), [update, error, clearUpdate, userActiveParty, setUserActiveParty, isConnected]);
}; 