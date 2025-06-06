import {
  createContext,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import { useQueueUpdates as useQueueUpdatesHook } from './hooks/useQueueUpdates';

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
  // Use the new unified hook for all functionality
  const hookResult = useQueueUpdatesHook();

  const contextValue = useMemo(() => ({ 
    queueUpdate: hookResult.queueUpdate, 
    error: hookResult.error, 
    clearUpdate: hookResult.clearUpdate,
    isConnected: hookResult.isConnected
  }), [hookResult]);

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