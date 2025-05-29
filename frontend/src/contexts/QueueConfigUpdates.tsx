import React, { createContext, useContext, useState, useEffect, ReactNode, useMemo, useCallback } from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';
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

export const QueueConfigProvider: React.FC<QueueConfigProviderProps> = ({ children }) => {
  const { user } = useAuth();
  const [queueConfig, setQueueConfig] = useState<QueueConfigDTO | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const handleQueueConfigUpdate = useCallback((message: any) => {
    try {
      console.log('[QueueConfig] Received queue config update:', message);
      
      if (message && typeof message === 'object') {
        setQueueConfig(message);
        setError(null);
      }
    } catch (err) {
      console.error('[QueueConfig] Error processing queue config update:', err);
      setError('Failed to process queue configuration update');
    }
  }, []);

  // Initialize WebSocket connection when user is authenticated
  useEffect(() => {
    if (user && user.id) {
      console.log('[QueueConfig] Connecting to queue config updates for user:', user.username);
      
      webSocketService.connect((message) => {
        // This is the general callback, we'll subscribe to specific topics
      });

      // Subscribe to queue config updates
      webSocketService.subscribe('/topic/queue/config', handleQueueConfigUpdate);
      
      setIsConnected(true);
    } else {
      console.log('[QueueConfig] User not authenticated, disconnecting...');
      webSocketService.disconnect();
      setIsConnected(false);
      setQueueConfig(null);
    }

    return () => {
      if (!user) {
        webSocketService.disconnect();
      }
    };
  }, [user, handleQueueConfigUpdate]);

  const contextValue = useMemo(() => ({
    queueConfig,
    isQueueEnabled: queueConfig?.queueEnabled ?? true, // Default to enabled
    isConnected,
    error,
    clearError
  }), [queueConfig, isConnected, error, clearError]);

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