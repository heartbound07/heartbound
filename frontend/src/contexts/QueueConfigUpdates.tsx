import { createContext, useContext, useState, useEffect, ReactNode, useMemo, useCallback, useRef } from 'react';
import webSocketService from '../config/WebSocketService';
import { useAuth } from '@/contexts/auth/useAuth';
import type { QueueConfigDTO } from '@/config/pairingService';
import { getPublicQueueStatus } from '@/config/pairingService';

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
  const { user } = useAuth();
  const [queueConfig, setQueueConfig] = useState<QueueConfigDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  
  const subscriptionRef = useRef<boolean>(false);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // Add function to fetch initial queue config
  const fetchInitialQueueConfig = useCallback(async () => {
    if (!user?.id) return;
    
    try {
      console.log('[QueueConfig] Fetching initial queue config...');
      const config = await getPublicQueueStatus();
      setQueueConfig(config);
      console.log('[QueueConfig] Initial queue config loaded:', config);
    } catch (err: any) {
      console.error('[QueueConfig] Error fetching initial queue config:', err);
      setError('Failed to fetch queue configuration');
    }
  }, [user?.id]);

  const handleQueueConfigUpdate = useCallback((message: QueueConfigDTO) => {
    console.info('[QueueConfig] Received queue config update:', message);
    setQueueConfig(message);
    setError(null);
  }, []);

  const subscribeToQueueConfig = useCallback(() => {
    if (subscriptionRef.current) {
      console.log('[QueueConfig] Already subscribed to queue config updates');
      return;
    }

    try {
      console.log('[QueueConfig] Subscribing to queue config updates...');
      
      const subscription = webSocketService.subscribe('/topic/queue/config', handleQueueConfigUpdate);
      
      if (subscription) {
        subscriptionRef.current = true;
        setIsConnected(true);
        console.log('[QueueConfig] Successfully subscribed to queue config updates');
      } else {
        console.warn('[QueueConfig] Failed to subscribe to queue config updates');
        setError('Failed to connect to queue config updates');
        setIsConnected(false);
      }
    } catch (err: any) {
      console.error('[QueueConfig] Error subscribing to queue config updates:', err);
      setError('Queue config subscription error');
      setIsConnected(false);
    }
  }, [handleQueueConfigUpdate]);

  // Cleanup function
  const cleanup = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
    
    if (subscriptionRef.current) {
      console.log('[QueueConfig] Cleaning up subscription...');
      subscriptionRef.current = false;
      setIsConnected(false);
    }
  }, []);

  // Initialize when user is authenticated
  useEffect(() => {
    if (!user?.id) {
      console.log('[QueueConfig] User not authenticated, cleaning up...');
      cleanup();
      setQueueConfig(null);
      return;
    }

    console.log('[QueueConfig] User authenticated, initializing queue config for:', user.username);

    // First, fetch the initial queue config
    fetchInitialQueueConfig();

    // Then set up WebSocket subscription
    const attemptSubscription = () => {
      if (webSocketService.isConnected()) {
        subscribeToQueueConfig();
      } else {
        console.log('[QueueConfig] WebSocket not ready, retrying in 1 second...');
        timeoutRef.current = setTimeout(attemptSubscription, 1000);
      }
    };

    // Small delay to ensure WebSocket is ready
    timeoutRef.current = setTimeout(attemptSubscription, 100);

    // Cleanup on unmount or user change
    return cleanup;
  }, [user?.id, fetchInitialQueueConfig, subscribeToQueueConfig, cleanup]);

  // Cleanup on unmount
  useEffect(() => {
    return cleanup;
  }, [cleanup]);

  const contextValue = useMemo(() => ({
    queueConfig,
    isQueueEnabled: queueConfig?.queueEnabled ?? true, // Default to enabled until we get initial config
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