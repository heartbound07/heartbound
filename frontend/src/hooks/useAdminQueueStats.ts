import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '@/contexts/auth/useAuth';
import { getQueueStatistics, type QueueStatsDTO } from '@/config/pairingService';

interface AdminQueueStatsHookReturn {
  queueStats: QueueStatsDTO | null;
  error: string | null;
  isConnected: boolean;
  isLoading: boolean;
  clearError: () => void;
  retryConnection: () => void;
}

export const useAdminQueueStats = (): AdminQueueStatsHookReturn => {
  const { isConnected, subscribe } = useWebSocket();
  const { hasRole } = useAuth();
  const [queueStats, setQueueStats] = useState<QueueStatsDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Only subscribe if user is an admin
  const isAdmin = hasRole('ADMIN');

  // WebSocket subscription for live updates
  useEffect(() => {
    if (!isAdmin || !isConnected) {
      return;
    }

    console.log('[AdminQueueStats] Setting up WebSocket subscription to /topic/admin/queue-stats');

    const unsubscribe = subscribe<QueueStatsDTO>(
      '/topic/admin/queue-stats',
      (message: QueueStatsDTO) => {
        console.log('[AdminQueueStats] Received admin queue stats update:', message);
        setQueueStats(message);
        setError(null);
        setIsLoading(false);
      }
    );

    return () => {
      console.log('[AdminQueueStats] Cleaning up WebSocket subscription');
      unsubscribe();
    };
  }, [subscribe, isConnected, isAdmin]);

  // Initial data fetch
  useEffect(() => {
    if (!isAdmin) {
      setIsLoading(false);
      return;
    }

    const fetchInitialStats = async () => {
      try {
        setIsLoading(true);
        setError(null);
        console.log('[AdminQueueStats] Fetching initial queue statistics');
        
        const stats = await getQueueStatistics();
        setQueueStats(stats);
        console.log('[AdminQueueStats] Initial stats loaded:', stats);
      } catch (err: any) {
        console.error('[AdminQueueStats] Error fetching initial stats:', err);
        setError(err.message || 'Failed to load queue statistics');
      } finally {
        setIsLoading(false);
      }
    };

    fetchInitialStats();
  }, [isAdmin]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const retryConnection = useCallback(() => {
    if (!isAdmin) return;
    
    setError(null);
    setIsLoading(true);
    
    // Re-fetch initial data
    getQueueStatistics()
      .then((stats) => {
        setQueueStats(stats);
        setIsLoading(false);
      })
      .catch((err) => {
        setError(err.message || 'Failed to retry connection');
        setIsLoading(false);
      });
  }, [isAdmin]);

  return useMemo(() => ({
    queueStats,
    error,
    isConnected: isConnected && isAdmin,
    isLoading,
    clearError,
    retryConnection,
  }), [queueStats, error, isConnected, isAdmin, isLoading, clearError, retryConnection]);
}; 