import { useState, useEffect, useCallback, useMemo } from 'react';
import { useWebSocket } from './useWebSocket';
import { useAuth } from '@/contexts/auth/useAuth';
import { getQueueStatistics, warmUpQueueStatsCache, type QueueStatsDTO } from '@/config/pairingService';

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

    // After subscribing, trigger a cache warm-up to ensure we get initial data
    // This helps with the optimized backend that only broadcasts when admins are connected
    const warmUpCache = async () => {
      try {
        console.log('[AdminQueueStats] Warming up cache after subscription');
        const response = await fetch('/pairings/admin/queue/cache/warmup', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          }
        });
        if (response.ok) {
          console.log('[AdminQueueStats] Cache warmed up successfully');
        }
      } catch (error) {
        console.log('[AdminQueueStats] Cache warm-up failed, will rely on initial fetch:', error);
      }
    };

    // Warm up cache after a short delay to ensure subscription is established
    const warmUpTimer = setTimeout(warmUpCache, 500);

    return () => {
      console.log('[AdminQueueStats] Cleaning up WebSocket subscription');
      clearTimeout(warmUpTimer);
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

  // Periodic refresh as fallback to ensure data stays current
  // This provides resilience in case WebSocket updates are missed
  useEffect(() => {
    if (!isAdmin || !isConnected) {
      return;
    }

    const refreshInterval = setInterval(async () => {
      try {
        console.log('[AdminQueueStats] Periodic refresh of queue statistics');
        const stats = await getQueueStatistics();
        setQueueStats(stats);
        setError(null);
      } catch (err: any) {
        console.warn('[AdminQueueStats] Periodic refresh failed:', err);
        // Don't set error on periodic refresh failures to avoid noise
      }
    }, 30000); // Refresh every 30 seconds as fallback

    return () => {
      clearInterval(refreshInterval);
    };
  }, [isAdmin, isConnected]);

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