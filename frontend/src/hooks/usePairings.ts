import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth/useAuth';
import { 
  getCurrentUserPairing, 
  getPairingHistory, 
  joinMatchmakingQueue, 
  leaveMatchmakingQueue, 
  getQueueStatus,
  type PairingDTO, 
  type JoinQueueRequestDTO, 
  type QueueStatusDTO 
} from '@/config/pairingService';
import { getUserProfile, type UserProfileDTO } from '@/config/userService';

export const usePairings = () => {
  const { user } = useAuth();
  const [currentPairing, setCurrentPairing] = useState<PairingDTO | null>(null);
  const [pairingHistory, setPairingHistory] = useState<PairingDTO[]>([]);
  const [queueStatus, setQueueStatus] = useState<QueueStatusDTO>({ inQueue: false });
  const [pairedUser, setPairedUser] = useState<UserProfileDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Fetch current pairing and queue status
  const fetchPairingData = useCallback(async () => {
    if (!user?.id) return;

    try {
      setLoading(true);
      setError(null);

      const [pairing, history, status] = await Promise.all([
        getCurrentUserPairing(user.id),
        getPairingHistory(user.id),
        getQueueStatus(user.id)
      ]);

      setCurrentPairing(pairing);
      setPairingHistory(history);
      setQueueStatus(status);

      // If user has an active pairing, fetch the paired user's profile
      if (pairing) {
        const otherUserId = pairing.user1Id === user.id ? pairing.user2Id : pairing.user1Id;
        const otherUserProfile = await getUserProfile(otherUserId);
        setPairedUser(otherUserProfile);
      } else {
        setPairedUser(null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch pairing data');
      console.error('Error fetching pairing data:', err);
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  // Join matchmaking queue
  const joinQueue = useCallback(async (preferences: JoinQueueRequestDTO) => {
    try {
      setActionLoading(true);
      setError(null);
      await joinMatchmakingQueue(preferences);
      // Refresh data after joining
      await fetchPairingData();
    } catch (err: any) {
      setError(err.message || 'Failed to join matchmaking queue');
      throw err;
    } finally {
      setActionLoading(false);
    }
  }, [fetchPairingData]);

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

  // Initial data fetch
  useEffect(() => {
    fetchPairingData();
  }, [fetchPairingData]);

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
    refreshData: fetchPairingData
  };
}; 