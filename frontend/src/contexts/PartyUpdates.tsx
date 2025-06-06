import {
  createContext,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import { usePartyUpdates as usePartyUpdatesHook } from './hooks/usePartyUpdates';

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
  // Use the new unified hook for all functionality
  const hookResult = usePartyUpdatesHook();

  // Memoize context value to avoid unnecessary re-renders
  const contextValue = useMemo(() => ({ 
    update: hookResult.update, 
    error: hookResult.error, 
    clearUpdate: hookResult.clearUpdate, 
    userActiveParty: hookResult.userActiveParty,
    setUserActiveParty: hookResult.setUserActiveParty
  }), [hookResult]);

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
