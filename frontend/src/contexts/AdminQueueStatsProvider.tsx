import {
  createContext,
  ReactNode,
  useContext,
  useMemo,
} from 'react';
import { useAdminQueueStats as useAdminQueueStatsHook } from '@/hooks/useAdminQueueStats';
import type { QueueStatsDTO } from '@/config/pairingService';

interface AdminQueueStatsContextProps {
  queueStats: QueueStatsDTO | null;
  error: string | null;
  isConnected: boolean;
  isLoading: boolean;
  clearError: () => void;
  retryConnection: () => void;
}

const AdminQueueStatsContext = createContext<AdminQueueStatsContextProps | undefined>(undefined);

interface AdminQueueStatsProviderProps {
  children: ReactNode;
}

export const AdminQueueStatsProvider = ({ children }: AdminQueueStatsProviderProps) => {
  // Use the unified hook for all functionality
  const hookResult = useAdminQueueStatsHook();

  const contextValue = useMemo(() => ({ 
    queueStats: hookResult.queueStats, 
    error: hookResult.error, 
    isConnected: hookResult.isConnected,
    isLoading: hookResult.isLoading,
    clearError: hookResult.clearError,
    retryConnection: hookResult.retryConnection
  }), [hookResult]);

  return (
    <AdminQueueStatsContext.Provider value={contextValue}>
      {children}
    </AdminQueueStatsContext.Provider>
  );
};

export const useAdminQueueStats = (): AdminQueueStatsContextProps => {
  const context = useContext(AdminQueueStatsContext);
  if (!context) {
    throw new Error('useAdminQueueStats must be used within an AdminQueueStatsProvider');
  }
  return context;
}; 