import {
  createContext,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import { usePairingUpdates as usePairingUpdatesHook } from './hooks/usePairingUpdates';
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
  // Use the new unified hook for all functionality
  const hookResult = usePairingUpdatesHook();

  const contextValue = useMemo(() => ({ 
    pairingUpdate: hookResult.pairingUpdate, 
    error: hookResult.error, 
    clearUpdate: hookResult.clearUpdate,
    isConnected: hookResult.isConnected,
    retryConnection: hookResult.retryConnection
  }), [hookResult]);

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