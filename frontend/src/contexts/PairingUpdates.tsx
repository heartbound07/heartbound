import {
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
import type { PairingDTO } from '@/config/pairingService';

export interface PairingUpdateEvent {
  eventType: 'MATCH_FOUND' | 'PAIRING_ENDED';
  pairing?: PairingDTO;
  message: string;
  timestamp: string;
}

interface PairingUpdatesContextProps {
  pairingUpdate: PairingUpdateEvent | null;
  error: string | null;
  clearUpdate: () => void;
  isConnected: boolean;
}

const PairingUpdatesContext = createContext<PairingUpdatesContextProps | undefined>(undefined);

interface PairingUpdatesProviderProps {
  children: ReactNode;
}

export const PairingUpdatesProvider = ({ children }: PairingUpdatesProviderProps) => {
  const { isAuthenticated, tokens, user } = useAuth();
  const [pairingUpdate, setPairingUpdate] = useState<PairingUpdateEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isConnected, setIsConnected] = useState(false);

  const clearUpdate = useCallback(() => {
    setPairingUpdate(null);
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !tokens?.accessToken || !user?.id) {
      setIsConnected(false);
      return;
    }

    if (isConnecting) return;
    
    setIsConnecting(true);
    
    const connectionTimer = setTimeout(() => {
      try {
        // Subscribe to user-specific pairing updates
        const subscription = webSocketService.subscribe(`/user/${user.id}/topic/pairings`, (message: PairingUpdateEvent) => {
          console.info('[PairingUpdates] Received pairing update:', message);
          setPairingUpdate(message);
          setIsConnected(true);
        });

        if (subscription) {
          setIsConnected(true);
        }
      } catch (err: any) {
        console.error('[PairingUpdates] Error connecting to pairing WebSocket:', err);
        setError('Pairing WebSocket connection error');
        setIsConnected(false);
      } finally {
        setIsConnecting(false);
      }
    }, 1000); // Slight delay after other connections

    return () => {
      clearTimeout(connectionTimer);
      setIsConnecting(false);
    };
  }, [isAuthenticated, tokens, user?.id]);

  const contextValue = useMemo(() => ({ 
    pairingUpdate, 
    error, 
    clearUpdate,
    isConnected
  }), [pairingUpdate, error, clearUpdate, isConnected]);

  return (
    <PairingUpdatesContext.Provider value={contextValue}>
      {children}
    </PairingUpdatesContext.Provider>
  );
};

export const usePairingUpdates = (): PairingUpdatesContextProps => {
  const context = useContext(PairingUpdatesContext);
  if (!context) {
    throw new Error('usePairingUpdates must be used within a PairingUpdatesProvider');
  }
  return context;
};

export default PairingUpdatesProvider; 