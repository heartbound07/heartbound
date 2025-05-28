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
import type { QueueStatusDTO } from '@/config/pairingService';

export interface QueueUpdateEvent {
  totalQueueSize: number;
  timestamp?: string;
}

interface QueueUpdatesContextProps {
  queueUpdate: QueueUpdateEvent | null;
  error: string | null;
  clearUpdate: () => void;
  isConnected: boolean;
}

const QueueUpdatesContext = createContext<QueueUpdatesContextProps | undefined>(undefined);

interface QueueUpdatesProviderProps {
  children: ReactNode;
}

export const QueueUpdatesProvider = ({ children }: QueueUpdatesProviderProps) => {
  const { isAuthenticated, tokens } = useAuth();
  const [queueUpdate, setQueueUpdate] = useState<QueueUpdateEvent | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isConnected, setIsConnected] = useState(false);

  const clearUpdate = useCallback(() => {
    setQueueUpdate(null);
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !tokens?.accessToken) {
      setIsConnected(false);
      return;
    }

    if (isConnecting) return;
    
    setIsConnecting(true);
    
    const connectionTimer = setTimeout(() => {
      try {
        // Subscribe to queue updates
        const subscription = webSocketService.subscribe('/topic/queue', (message: QueueUpdateEvent) => {
          console.info('[QueueUpdates] Received queue update:', message);
          setQueueUpdate(message);
          setIsConnected(true);
        });

        if (subscription) {
          setIsConnected(true);
        }
      } catch (err: any) {
        console.error('[QueueUpdates] Error connecting to queue WebSocket:', err);
        setError('Queue WebSocket connection error');
        setIsConnected(false);
      } finally {
        setIsConnecting(false);
      }
    }, 500);

    return () => {
      clearTimeout(connectionTimer);
      setIsConnecting(false);
    };
  }, [isAuthenticated, tokens]);

  const contextValue = useMemo(() => ({ 
    queueUpdate, 
    error, 
    clearUpdate,
    isConnected
  }), [queueUpdate, error, clearUpdate, isConnected]);

  return (
    <QueueUpdatesContext.Provider value={contextValue}>
      {children}
    </QueueUpdatesContext.Provider>
  );
};

export const useQueueUpdates = (): QueueUpdatesContextProps => {
  const context = useContext(QueueUpdatesContext);
  if (!context) {
    throw new Error('useQueueUpdates must be used within a QueueUpdatesProvider');
  }
  return context;
};

export default QueueUpdatesProvider; 