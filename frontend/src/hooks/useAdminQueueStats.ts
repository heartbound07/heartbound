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
  refreshStats: () => Promise<void>;
  isRefreshing: boolean;
}

export const useAdminQueueStats = (): AdminQueueStatsHookReturn => {
  const { isConnected, subscribe } = useWebSocket();
  const { hasRole } = useAuth();
  const [queueStats, setQueueStats] = useState<QueueStatsDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

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

  // Manual refresh function for on-demand statistics fetching
  const refreshStats = useCallback(async () => {
    if (!isAdmin) return;
    
    try {
      setIsRefreshing(true);
      setError(null);
      console.log('[AdminQueueStats] Manual refresh of queue statistics');
      
      // Trigger server-side refresh and get fresh data
      const response = await fetch('/pairings/admin/queue/statistics/refresh', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
          'Content-Type': 'application/json'
        }
      });

      if (response.ok) {
        const stats = await response.json();
        setQueueStats(stats);
        console.log('[AdminQueueStats] Manual refresh completed successfully');
      } else {
        throw new Error(`Failed to refresh statistics: ${response.statusText}`);
      }
    } catch (err: any) {
      console.error('[AdminQueueStats] Manual refresh failed:', err);
      setError(err.message || 'Failed to refresh statistics');
    } finally {
      setIsRefreshing(false);
    }
  }, [isAdmin]);

  // Reduced frequency fallback refresh (only when no WebSocket updates)
  // This is now primarily a safety net rather than the main data source
  useEffect(() => {
    if (!isAdmin || !isConnected) {
      return;
    }

    const refreshInterval = setInterval(async () => {
      // Only do fallback refresh if we haven't had recent updates
      try {
        console.log('[AdminQueueStats] Fallback refresh of queue statistics');
        const stats = await getQueueStatistics();
        setQueueStats(stats);
        setError(null);
      } catch (err: any) {
        console.warn('[AdminQueueStats] Fallback refresh failed:', err);
        // Don't set error on fallback refresh failures to avoid noise
      }
    }, 120000); // Reduced to every 2 minutes as fallback only

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
    refreshStats,
    isRefreshing,
  }), [queueStats, error, isConnected, isAdmin, isLoading, clearError, retryConnection, refreshStats, isRefreshing]);
}; 