import React, {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import webSocketService from '../config/WebSocketService';

interface PartyUpdatesContextProps {
  update: string | null;
  error: string | null;
}

const PartyUpdatesContext = createContext<PartyUpdatesContextProps | undefined>(undefined);

interface PartyUpdatesProviderProps {
  children: ReactNode;
}

export const PartyUpdatesProvider = ({ children }: PartyUpdatesProviderProps) => {
  const [update, setUpdate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      // Establish the WebSocket connection and subscribe to party updates
      webSocketService.connect((message: string) => {
        console.info('[PartyUpdates] Received update:', message);
        setUpdate(message);
      });
    } catch (err: any) {
      console.error('[PartyUpdates] Error connecting to WebSocket:', err);
      setError('WebSocket connection error');
    }

    // Clean up the connection when the provider unmounts
    return () => {
      webSocketService.disconnect();
    };
  }, []);

  // Memoize context value to prevent unnecessary re-renders
  const contextValue = useMemo(() => ({ update, error }), [update, error]);

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
