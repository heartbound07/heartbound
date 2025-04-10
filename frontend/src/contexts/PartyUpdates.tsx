import React, {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useContext,
  useMemo,
  useCallback,
} from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';

// Define the TypeScript interface that mirrors our backend's LFGPartyEventDTO
export interface LFGPartyEvent {
  eventType: string;
  party?: any; // Make optional since minimal events might not include it
  minimalParty?: {
    id: string;
    userId: string;
    status: string;
    participants: string[];
    joinRequests: string[];
    invitedUsers: string[];
    trackingStatus?: string;
    currentTrackedMatchId?: string;
  };
  message: string;
  targetUserId?: string;
}

interface PartyUpdatesContextProps {
  update: LFGPartyEvent | null;
  error: string | null;
  clearUpdate: () => void;
  userActiveParty: string | null;
  setUserActiveParty: (partyId: string | null) => void;
}

const PartyUpdatesContext = createContext<PartyUpdatesContextProps | undefined>(undefined);

interface PartyUpdatesProviderProps {
  children: ReactNode;
}

export const PartyUpdatesProvider = ({ children }: PartyUpdatesProviderProps) => {
  const { isAuthenticated, tokens, user } = useAuth();
  const [update, setUpdate] = useState<LFGPartyEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [userActiveParty, setUserActiveParty] = useState<string | null>(null);

  // Memoize the clear update function to avoid unnecessary re-renders
  const clearUpdate = useCallback(() => {
    setUpdate(null);
  }, []);

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

  useEffect(() => {
    if (!isAuthenticated || !tokens || !tokens.accessToken) {
      // Do not connect if user is not authenticated or tokens aren't available
      return;
    }

    // Prevent multiple connection attempts
    if (isConnecting) return;
    
    setIsConnecting(true);
    
    // Small delay to ensure token is properly saved to localStorage
    const connectionTimer = setTimeout(() => {
      try {
        // Connect and subscribe to the default /topic/party endpoint.
        webSocketService.connect((message: LFGPartyEvent) => {
          console.info('[PartyUpdates] Received update:', message);
          setUpdate(message);
        });
      } catch (err: any) {
        console.error('[PartyUpdates] Error connecting to WebSocket:', err);
        setError('WebSocket connection error');
      } finally {
        setIsConnecting(false);
      }
    }, 500);

    // Clean up the connection and timer
    return () => {
      clearTimeout(connectionTimer);
      webSocketService.disconnect();
      setIsConnecting(false);
    };
  }, [isAuthenticated, tokens]);

  // Memoize context value to avoid unnecessary re-renders
  const contextValue = useMemo(() => ({ 
    update, 
    error, 
    clearUpdate, 
    userActiveParty,
    setUserActiveParty
  }), [update, error, clearUpdate, userActiveParty]);

  return (
    <PartyUpdatesContext.Provider value={contextValue}>
      {children}
    </PartyUpdatesContext.Provider>
  );
};

export const usePartyUpdates = (): PartyUpdatesContextProps => {
  const context = useContext(PartyUpdatesContext);
  if (!context) {
    throw new Error('usePartyUpdates must be used within a PartyUpdatesProvider');
  }
  return context;
};
export default PartyUpdatesProvider;

