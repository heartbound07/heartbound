import React, { createContext, useContext, useState, useEffect, ReactNode, useMemo, useCallback, useRef } from 'react';
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
  const subscriptionRef = useRef<boolean>(false);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

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

  // Subscribe to queue config updates with proper connection handling
  const subscribeToQueueConfig = useCallback(() => {
    if (subscriptionRef.current) {
      console.log('[QueueConfig] Already subscribed, skipping...');
      return;
    }

    try {
      console.log('[QueueConfig] Subscribing to queue config updates...');
      webSocketService.subscribe('/topic/queue/config', handleQueueConfigUpdate);
      subscriptionRef.current = true;
      setIsConnected(true);
      setError(null);
    } catch (err) {
      console.error('[QueueConfig] Failed to subscribe:', err);
      setError('Failed to connect to queue configuration updates');
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
      // Note: webSocketService handles unsubscription internally
      subscriptionRef.current = false;
      setIsConnected(false);
    }
  }, []);

  // Initialize WebSocket connection when user is authenticated
  useEffect(() => {
    if (!user?.id) {
      console.log('[QueueConfig] User not authenticated, cleaning up...');
      cleanup();
      setQueueConfig(null);
      return;
    }

    console.log('[QueueConfig] Connecting to queue config updates for user:', user.username);

    // Check if WebSocket is ready, if not wait a bit
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
  }, [user?.id, subscribeToQueueConfig, cleanup]);

  // Cleanup on unmount
  useEffect(() => {
    return cleanup;
  }, [cleanup]);

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