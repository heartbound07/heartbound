import React, {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';

// Define the TypeScript interface that mirrors our backend's LFGPartyEventDTO
export interface LFGPartyEvent {
  eventType: string;
  party: any; // You can later replace "any" with a proper type for LFGPartyResponseDTO
  message: string;
}

interface PartyUpdatesContextProps {
  update: LFGPartyEvent | null;
  error: string | null;
  clearUpdate: () => void;
}

const PartyUpdatesContext = createContext<PartyUpdatesContextProps | undefined>(undefined);

interface PartyUpdatesProviderProps {
  children: ReactNode;
}

export const PartyUpdatesProvider = ({ children }: PartyUpdatesProviderProps) => {
  const { isAuthenticated } = useAuth();
  const [update, setUpdate] = useState<LFGPartyEvent | null>(null);
  const [error, setError] = useState<string | null>(null);

  const clearUpdate = () => setUpdate(null);

  useEffect(() => {
    if (!isAuthenticated) {
      // Do not connect if the user is not authenticated.
      return;
    }

    try {
      // Connect and subscribe to the default /topic/party endpoint.
      webSocketService.connect((message: LFGPartyEvent) => {
        console.info('[PartyUpdates] Received update:', message);
        setUpdate(message);
      });
    } catch (err: any) {
      console.error('[PartyUpdates] Error connecting to WebSocket:', err);
      setError('WebSocket connection error');
    }

    // Clean up the connection
    return () => {
      webSocketService.disconnect();
    };
  }, [isAuthenticated]);

  // Memoize to avoid unnecessary re-renders.
  const contextValue = useMemo(() => ({ update, error, clearUpdate }), [update, error]);

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
