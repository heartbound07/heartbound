import { createContext, useContext, ReactNode, useMemo } from 'react';
import { useQueueConfig as useQueueConfigHook } from '../hooks/useQueueConfig';
import type { QueueConfigDTO } from '@/config/pairingService';

interface QueueConfigContextProps {
  queueConfig: QueueConfigDTO | null;
  isQueueEnabled: boolean;
  isConnected: boolean;
  error: string | null;
  clearError: () => void;
}

const QueueConfigContext = createContext<QueueConfigContextProps | undefined>(undefined);

interface QueueConfigProviderProps {
  children: ReactNode;
}

export const QueueConfigProvider = ({ children }: QueueConfigProviderProps) => {
  // Use the new unified hook for all functionality
  const hookResult = useQueueConfigHook();

  const contextValue = useMemo(() => ({
    queueConfig: hookResult.queueConfig,
    isQueueEnabled: hookResult.isQueueEnabled,
    isConnected: hookResult.isConnected,
    error: hookResult.error,
    clearError: hookResult.clearError
  }), [hookResult]);

  return (
    <QueueConfigContext.Provider value={contextValue}>
      {children}
    </QueueConfigContext.Provider>
  );
};

export const useQueueConfig = (): QueueConfigContextProps => {
  const context = useContext(QueueConfigContext);
  if (!context) {
    throw new Error('useQueueConfig must be used within a QueueConfigProvider');
  }
  return context;
}; 