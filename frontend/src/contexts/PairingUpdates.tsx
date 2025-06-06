import {
  createContext,
  useState,
  useEffect,
  ReactNode,
  useContext,
  useMemo,
  useCallback,
  useRef,
} from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';
import type { PairingDTO } from '@/config/pairingService';

export interface PairingUpdateEvent {
  eventType: 'MATCH_FOUND' | 'PAIRING_ENDED' | 'NO_MATCH_FOUND' | 'QUEUE_REMOVED';
  pairing?: PairingDTO;
  message: string;
  timestamp: string;
  totalInQueue?: number; // For NO_MATCH_FOUND events
}

interface PairingUpdatesContextProps {
  pairingUpdate: PairingUpdateEvent | null;
  error: string | null;
  clearUpdate: () => void;
  isConnected: boolean;
  retryConnection: () => void;
}

const PairingUpdatesContext = createContext<PairingUpdatesContextProps | undefined>(undefined);

interface PairingUpdatesProviderProps {
  children: ReactNode;
}

export const PairingUpdatesProvider = ({ children }: PairingUpdatesProviderProps) => {
  const { isAuthenticated, tokens, user } = useAuth();
  const [pairingUpdate, setPairingUpdate] = useState<PairingUpdateEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  
  // Use refs to avoid dependency issues
  const retryCountRef = useRef(0);
  const isConnectingRef = useRef(false);
  const hasSubscriptionRef = useRef(false);
  const maxRetries = 3;

  const clearUpdate = useCallback(() => {
    setPairingUpdate(null);
  }, []);

  const establishConnection = useCallback(() => {
    // Prevent multiple simultaneous connection attempts
    if (isConnectingRef.current || !isAuthenticated || !tokens?.accessToken || !user?.id) {
      return;
    }

    // Don't reconnect if we already have a subscription
    if (hasSubscriptionRef.current && isConnected) {
      console.log(`[PairingUpdates] Already connected and subscribed for user: ${user.id}`);
      return;
    }

    isConnectingRef.current = true;
    setError(null);

    try {
      console.log(`[PairingUpdates] Establishing connection for user: ${user.id}`);
      
      const subscription = webSocketService.subscribe(
        `/user/${user.id}/topic/pairings`, 
        (message: PairingUpdateEvent) => {
          console.info('[PairingUpdates] Received pairing update:', message);
          console.info('[PairingUpdates] Message type:', typeof message);
          console.info('[PairingUpdates] Message eventType:', message.eventType);
          console.info('[PairingUpdates] Message pairing:', message.pairing);
          setPairingUpdate(message);
          setIsConnected(true);
          retryCountRef.current = 0;
        }
      );

      if (subscription) {
        setIsConnected(true);
        hasSubscriptionRef.current = true;
        retryCountRef.current = 0;
        console.log(`[PairingUpdates] Successfully subscribed to pairing updates for user: ${user.id}`);
      } else {
        throw new Error('Failed to create subscription');
      }
    } catch (err: any) {
      console.error('[PairingUpdates] Error connecting to pairing WebSocket:', err);
      setError('Pairing WebSocket connection error');
      setIsConnected(false);
      hasSubscriptionRef.current = false;
      
      // Retry connection if under max retries
      if (retryCountRef.current < maxRetries) {
        const retryDelay = Math.min(1000 * Math.pow(2, retryCountRef.current), 10000);
        console.log(`[PairingUpdates] Retrying connection in ${retryDelay}ms (attempt ${retryCountRef.current + 1}/${maxRetries})`);
        
        setTimeout(() => {
          retryCountRef.current += 1;
          isConnectingRef.current = false;
          establishConnection();
        }, retryDelay);
      } else {
        console.error('[PairingUpdates] Max retries reached for pairing WebSocket connection');
        isConnectingRef.current = false;
      }
    } finally {
      if (retryCountRef.current >= maxRetries) {
        isConnectingRef.current = false;
      } else if (hasSubscriptionRef.current) {
        isConnectingRef.current = false;
      }
    }
  }, [isAuthenticated, tokens?.accessToken, user?.id, isConnected]);

  // Reset connection state when auth changes
  useEffect(() => {
    if (!isAuthenticated || !tokens?.accessToken || !user?.id) {
      setIsConnected(false);
      hasSubscriptionRef.current = false;
      retryCountRef.current = 0;
      isConnectingRef.current = false;
      return;
    }

    // Only establish connection if we don't already have one
    if (!hasSubscriptionRef.current) {
      // Small delay to ensure WebSocket service is ready
      const connectionTimer = setTimeout(() => {
        establishConnection();
      }, 1000);

      return () => {
        clearTimeout(connectionTimer);
      };
    }
  }, [isAuthenticated, tokens?.accessToken, user?.id, establishConnection]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      hasSubscriptionRef.current = false;
      isConnectingRef.current = false;
      retryCountRef.current = 0;
    };
  }, []);

  const contextValue = useMemo(() => ({ 
    pairingUpdate, 
    error, 
    clearUpdate,
    isConnected,
    retryConnection: establishConnection
  }), [pairingUpdate, error, clearUpdate, isConnected, establishConnection]);

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