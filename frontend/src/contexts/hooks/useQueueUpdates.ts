import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import type { QueueUpdateEvent } from '../types/websocket';

interface QueueUpdatesHookReturn {
  queueUpdate: QueueUpdateEvent | null;
  error: string | null;
  clearUpdate: () => void;
  isConnected: boolean;
}

export const useQueueUpdates = (): QueueUpdatesHookReturn => {
  const { subscribe, lastError, isConnected } = useWebSocket();
  const [queueUpdate, setQueueUpdate] = useState<QueueUpdateEvent | null>(null);

  const clearUpdate = useCallback(() => {
    setQueueUpdate(null);
  }, []);

  // Extract error specific to queue updates
  const error = lastError?.type === 'server' && lastError.message.includes('/topic/queue')
    ? lastError.message
    : null;

  // Subscribe to queue updates
  useEffect(() => {
    console.log('[useQueueUpdates] Setting up subscription to /topic/queue');
    
    const unsubscribe = subscribe<QueueUpdateEvent>('/topic/queue', (message: QueueUpdateEvent) => {
      console.info('[useQueueUpdates] Received queue update:', message);
      setQueueUpdate(message);
    });

    return unsubscribe;
  }, [subscribe]);

  return useMemo(() => ({
    queueUpdate,
    error,
    clearUpdate,
    isConnected,
  }), [queueUpdate, error, clearUpdate, isConnected]);
}; 