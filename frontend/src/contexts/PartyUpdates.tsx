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
  const { isAuthenticated, tokens } = useAuth();
  const [update, setUpdate] = useState<LFGPartyEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);

  const clearUpdate = () => setUpdate(null);

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
