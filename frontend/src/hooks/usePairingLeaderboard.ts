import { useState, useCallback, useEffect } from 'react';
import { getPairingLeaderboard, type PairingLeaderboardDTO } from '@/config/pairingService';

interface UsePairingLeaderboardResult {
  data: PairingLeaderboardDTO[];
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * Hook for fetching pairing leaderboard data
 * Provides ranked list of active pairings with embedded user profiles
 */
export function usePairingLeaderboard(): UsePairingLeaderboardResult {
  const [data, setData] = useState<PairingLeaderboardDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLeaderboard = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const leaderboardData = await getPairingLeaderboard();
      setData(leaderboardData);
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || 'Failed to load leaderboard';
      setError(errorMessage);
      console.error('Error fetching pairing leaderboard:', err);
      setData([]); // Clear data on error
    } finally {
      setLoading(false);
    }
  }, []);

  const refetch = useCallback(async () => {
    await fetchLeaderboard();
  }, [fetchLeaderboard]);

  // Auto-fetch on mount
  useEffect(() => {
    fetchLeaderboard();
  }, [fetchLeaderboard]);

  return {
    data,
    loading,
    error,
    refetch
  };
} 