import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '@/contexts/auth/useAuth';
import type { PairingUpdateEvent } from '../contexts/types/websocket';

interface PairingUpdatesHookReturn {
  pairingUpdate: PairingUpdateEvent | null;
  error: string | null;
  clearUpdate: () => void;
  isConnected: boolean;
  retryConnection: () => void;
}

export const usePairingUpdates = (): PairingUpdatesHookReturn => {
  const { subscribe, lastError, isConnected, reconnect } = useWebSocket();
  const { user } = useAuth();
  const [pairingUpdate, setPairingUpdate] = useState<PairingUpdateEvent | null>(null);

  const clearUpdate = useCallback(() => {
    setPairingUpdate(null);
  }, []);

  // Extract error specific to pairing updates
  const error = lastError?.type === 'server' && lastError.message.includes('/user/') && lastError.message.includes('/topic/pairings')
    ? lastError.message
    : null;

  // Subscribe to user-specific pairing updates
  useEffect(() => {
    if (!user?.id) {
      console.log('[usePairingUpdates] No user ID available, skipping subscription');
      return;
    }

    const userTopic = `/user/${user.id}/topic/pairings`;
    console.log(`[usePairingUpdates] Setting up subscription to ${userTopic}`);
    
    const unsubscribe = subscribe<PairingUpdateEvent>(userTopic, (message: PairingUpdateEvent) => {
      console.info('[usePairingUpdates] Received pairing update:', message);
      console.info('[usePairingUpdates] Message type:', typeof message);
      console.info('[usePairingUpdates] Message eventType:', message.eventType);
      console.info('[usePairingUpdates] Message pairing:', message.pairing);
      setPairingUpdate(message);
    });

    return unsubscribe;
  }, [subscribe, user?.id]);

  return useMemo(() => ({
    pairingUpdate,
    error,
    clearUpdate,
    isConnected,
    retryConnection: reconnect,
  }), [pairingUpdate, error, clearUpdate, isConnected, reconnect]);
}; 