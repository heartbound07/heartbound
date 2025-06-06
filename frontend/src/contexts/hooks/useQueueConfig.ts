import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '@/contexts/auth/useAuth';
import { getPublicQueueStatus } from '@/config/pairingService';
import type { QueueConfigDTO } from '@/config/pairingService';

interface QueueConfigHookReturn {
  queueConfig: QueueConfigDTO | null;
  isQueueEnabled: boolean;
  isConnected: boolean;
  error: string | null;
  clearError: () => void;
}

export const useQueueConfig = (): QueueConfigHookReturn => {
  const { subscribe, lastError, isConnected, clearError: clearWebSocketError } = useWebSocket();
  const { user } = useAuth();
  const [queueConfig, setQueueConfig] = useState<QueueConfigDTO | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  const clearError = useCallback(() => {
    setLocalError(null);
    clearWebSocketError();
  }, [clearWebSocketError]);

  // Extract WebSocket error specific to queue config
  const wsError = lastError?.type === 'server' && lastError.message.includes('/topic/queue/config')
    ? lastError.message
    : null;

  // Combined error (local API error or WebSocket error)
  const error = localError || wsError;

  // Fetch initial queue config
  const fetchInitialQueueConfig = useCallback(async () => {
    if (!user?.id) return;
    
    try {
      console.log('[useQueueConfig] Fetching initial queue config...');
      const config = await getPublicQueueStatus();
      setQueueConfig(config);
      setLocalError(null);
      console.log('[useQueueConfig] Initial queue config loaded:', config);
    } catch (err: any) {
      console.error('[useQueueConfig] Error fetching initial queue config:', err);
      setLocalError('Failed to fetch queue configuration');
    }
  }, [user?.id]);

  // Subscribe to queue config updates
  useEffect(() => {
    if (!user?.id) {
      console.log('[useQueueConfig] No user ID available, skipping subscription');
      setQueueConfig(null);
      return;
    }

    console.log('[useQueueConfig] Setting up subscription to /topic/queue/config');
    
    const unsubscribe = subscribe<QueueConfigDTO>('/topic/queue/config', (message: QueueConfigDTO) => {
      console.info('[useQueueConfig] Received queue config update:', message);
      setQueueConfig(message);
      setLocalError(null);
    });

    return unsubscribe;
  }, [subscribe, user?.id]);

  // Fetch initial config when user is authenticated
  useEffect(() => {
    if (user?.id) {
      fetchInitialQueueConfig();
    }
  }, [user?.id, fetchInitialQueueConfig]);

  return useMemo(() => ({
    queueConfig,
    isQueueEnabled: queueConfig?.queueEnabled ?? true, // Default to enabled until we get initial config
    isConnected,
    error,
    clearError,
  }), [queueConfig, isConnected, error, clearError]);
}; 