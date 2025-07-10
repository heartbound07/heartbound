import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '@/contexts/auth/useAuth';
import { 
  getCurrentUserPairing, 
  getPairingHistory, 
  joinMatchmakingQueue, 
  leaveMatchmakingQueue, 
  getQueueStatus,
  deletePairingById,
  clearInactivePairingHistory,
  unpairUsers,
  breakupPairing as breakupPairingAPI,
  type PairingDTO, 
  type QueueStatusDTO 
} from '@/config/pairingService';
import { getUserProfile, type UserProfileDTO } from '@/config/userService';
import { useQueueUpdates } from '@/contexts/QueueUpdates';
import { usePairingUpdates } from '@/contexts/PairingUpdates';

export const usePairings = () => {
  const { user } = useAuth();
  const { queueUpdate } = useQueueUpdates();
  const { pairingUpdate, clearUpdate } = usePairingUpdates();
  const [currentPairing, setCurrentPairing] = useState<PairingDTO | null>(null);
  const [pairingHistory, setPairingHistory] = useState<PairingDTO[]>([]);
  const [queueStatus, setQueueStatus] = useState<QueueStatusDTO>({ inQueue: false });
  const [pairedUser, setPairedUser] = useState<UserProfileDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Add abort controller for cleanup
  const abortControllerRef = useRef<AbortController | null>(null);

  // Enhanced fetch with abort signal
  const fetchPairingData = useCallback(async () => {
    if (!user?.id) return;

    // Cancel previous request if still pending
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    
    abortControllerRef.current = new AbortController();

    try {
      setLoading(true);
      setError(null);

      const [pairing, history, status] = await Promise.allSettled([
        getCurrentUserPairing(user.id),
        getPairingHistory(user.id),
        getQueueStatus(user.id)
      ]);

      // Handle results with proper error checking
      if (pairing.status === 'fulfilled') {
        setCurrentPairing(pairing.value);
      } else {
        const pairingError = pairing.reason as any;
        if (pairingError?.response?.status !== 404) {
          console.error("Failed to fetch current pairing:", pairingError)
          const errorMessage = pairingError?.response?.data?.message || pairingError?.message || "An unknown error occurred"
          setError(`Failed to load pairing status: ${errorMessage}`)
        } else {
          // It's a 404, which is expected if the user has no pairing.
          // This is common for ADMIN users who manage pairings but aren't in one themselves.
          // We shouldn't set an error state for this.
          setCurrentPairing(null)
          setError(null) // Clear any previous errors
        }
      }

      if (history.status === 'fulfilled') {
        setPairingHistory(history.value);
      } else {
        console.error('Failed to fetch pairing history:', history.reason);
      }

      if (status.status === 'fulfilled') {
        setQueueStatus(status.value);
      } else {
        console.error('Failed to fetch queue status:', status.reason);
        setQueueStatus({ inQueue: false }); // Safe fallback
      }

      // Fetch paired user profile if pairing exists
      if (pairing.status === 'fulfilled' && pairing.value) {
        const otherUserId = pairing.value.user1Id === user.id 
          ? pairing.value.user2Id 
          : pairing.value.user1Id;
        
        try {
          const otherUserProfile = await getUserProfile(otherUserId);
          setPairedUser(otherUserProfile);
        } catch (profileError) {
          console.error('Failed to fetch paired user profile:', profileError);
          setPairedUser(null);
        }
      } else {
        setPairedUser(null);
      }

    } catch (err: any) {
      if (!abortControllerRef.current?.signal.aborted) {
        setError(err.message || 'Failed to fetch pairing data');
        console.error('Error fetching pairing data:', err);
      }
    } finally {
      if (!abortControllerRef.current?.signal.aborted) {
        setLoading(false);
      }
    }
  }, [user?.id]);

  // Join queue now uses backend role-based selections
  const joinQueue = useCallback(async () => {
    if (!user?.id) {
      throw new Error('User authentication required');
    }

    try {
      setActionLoading(true);
      setError(null);
      
      await joinMatchmakingQueue({} as any); // Pass empty object to satisfy signature
      await fetchPairingData(); // Refresh data
      
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err.message || 'Failed to join queue';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setActionLoading(false);
    }
  }, [user?.id, fetchPairingData]);

  // Leave matchmaking queue
  const leaveQueue = useCallback(async () => {
    if (!user?.id) {
      setError('User ID is required to leave queue');
      return;
    }

    try {
      setActionLoading(true);
      setError(null);
      await leaveMatchmakingQueue(user.id);
      // Refresh data after leaving
      await fetchPairingData();
    } catch (err: any) {
      setError(err.message || 'Failed to leave matchmaking queue');
      throw err;
    } finally {
      setActionLoading(false);
    }
  }, [user?.id, fetchPairingData]);

  // Listen for queue updates and refresh status when queue size changes
  useEffect(() => {
    if (queueUpdate && user?.id) {
      console.log('Queue update received, updating queue count live...');
      // Update queue status with live count for immediate UI feedback
      setQueueStatus(prevStatus => ({
        ...prevStatus,
        totalQueueSize: queueUpdate.totalQueueSize
      }));
      
      // Also fetch fresh queue status to get accurate position
      getQueueStatus(user.id).then(status => {
        setQueueStatus(status);
      }).catch(err => {
        console.error('Error refreshing queue status:', err);
      });
    }
  }, [queueUpdate, user?.id]);

  // Listen for pairing updates and handle real-time activity updates
  useEffect(() => {
    if (pairingUpdate) {
      console.log('[usePairings] Received pairing update:', pairingUpdate);
      
      if (pairingUpdate.eventType === 'ACTIVITY_UPDATE' && pairingUpdate.pairing?.id === currentPairing?.id) {
        // Update the current pairing with the new activity data
        setCurrentPairing(prev => {
          if (!prev) return null;
          return { ...prev, ...pairingUpdate.pairing };
        });
        
        // Also update the pairing in history if it exists
        setPairingHistory(prev => 
          prev.map(pairing => 
            pairing.id === pairingUpdate.pairing?.id 
              ? { ...pairing, ...pairingUpdate.pairing }
              : pairing
          )
        );
        
        clearUpdate();
        console.log('[usePairings] Updated pairing statistics in real-time');
      }
    }
  }, [pairingUpdate, currentPairing?.id, clearUpdate]);

  // Initial data fetch
  useEffect(() => {
    fetchPairingData();
  }, [fetchPairingData]);

  // Cleanup effect
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  // Unpair users (admin function - keeps blacklist)
  const unpairPairing = useCallback(async (pairingId: number) => {
    try {
      setActionLoading(true);
      setError(null);
      
      await unpairUsers(pairingId);
      await fetchPairingData(); // Refresh data after unpairing
      
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err.message || 'Failed to unpair users';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setActionLoading(false);
    }
  }, [fetchPairingData]);

  // Delete specific pairing permanently (admin function - removes blacklist)
  const deletePairing = useCallback(async (pairingId: number) => {
    try {
      setActionLoading(true);
      setError(null);
      
      await deletePairingById(pairingId);
      await fetchPairingData(); // Refresh data after deletion
      
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err.message || 'Failed to delete pairing';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setActionLoading(false);
    }
  }, [fetchPairingData]);

  // Clear all inactive pairings
  const clearInactiveHistory = useCallback(async () => {
    try {
      setActionLoading(true);
      setError(null);
      
      const result = await clearInactivePairingHistory();
      await fetchPairingData(); // Refresh data after deletion
      
      return result;
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err.message || 'Failed to clear inactive history';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setActionLoading(false);
    }
  }, [fetchPairingData]);

  // Initiate breakup (user function)
  const breakupPairing = useCallback(async (pairingId: number, reason: string) => {
    if (!user?.id) {
      throw new Error('User authentication required');
    }

    if (!reason.trim()) {
      throw new Error('Breakup reason is required');
    }

    try {
      setActionLoading(true);
      setError(null);
      
      await breakupPairingAPI(pairingId, user.id, reason.trim());
      await fetchPairingData(); // Refresh data after breakup
      
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err.message || 'Failed to end match';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setActionLoading(false);
    }
  }, [user?.id, fetchPairingData]);

  return {
    currentPairing,
    pairingHistory,
    queueStatus,
    pairedUser,
    loading,
    error,
    actionLoading,
    joinQueue,
    leaveQueue,
    refreshData: fetchPairingData,
    unpairPairing,
    deletePairing,
    clearInactiveHistory,
    breakupPairing
  };
}; 