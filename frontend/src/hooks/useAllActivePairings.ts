import { useState, useEffect, useCallback, useRef } from 'react';
import { getAllActivePairings, type PairingDTO } from '@/config/pairingService';
import { usePairingUpdates } from '@/contexts/PairingUpdates';

export const useAllActivePairings = () => {
  const { pairingUpdate, clearUpdate } = usePairingUpdates();
  const [allActivePairings, setAllActivePairings] = useState<PairingDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Add abort controller for cleanup
  const abortControllerRef = useRef<AbortController | null>(null);

  // Fetch all active pairings
  const fetchAllActivePairings = useCallback(async () => {
    // Cancel previous request if still pending
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    
    abortControllerRef.current = new AbortController();

    try {
      setLoading(true);
      setError(null);

      const activePairings = await getAllActivePairings();
      setAllActivePairings(activePairings);

    } catch (err: any) {
      if (!abortControllerRef.current?.signal.aborted) {
        setError(err.message || 'Failed to fetch active pairings');
        console.error('Error fetching all active pairings:', err);
      }
    } finally {
      if (!abortControllerRef.current?.signal.aborted) {
        setLoading(false);
      }
    }
  }, []);

  // Listen for pairing updates and refresh when pairings change
  useEffect(() => {
    if (pairingUpdate) {
      console.log('[useAllActivePairings] Received pairing update:', pairingUpdate);
      
      // Refresh all active pairings when any pairing-related event occurs
      if (pairingUpdate.eventType === 'MATCH_FOUND' || 
          pairingUpdate.eventType === 'PAIRING_ENDED' ||
          pairingUpdate.eventType === 'ACTIVITY_UPDATE') {
        
        // For activity updates, we can update the specific pairing in place for better performance
        if (pairingUpdate.eventType === 'ACTIVITY_UPDATE' && pairingUpdate.pairing) {
          setAllActivePairings(prev => 
            prev.map(pairing => 
              pairing.id === pairingUpdate.pairing?.id 
                ? { ...pairing, ...pairingUpdate.pairing }
                : pairing
            )
          );
          clearUpdate();
          console.log('[useAllActivePairings] Updated pairing statistics in real-time');
        } else {
          // For other events, fetch fresh data
          fetchAllActivePairings();
          clearUpdate();
        }
      }
    }
  }, [pairingUpdate, fetchAllActivePairings, clearUpdate]);

  // Initial data fetch
  useEffect(() => {
    fetchAllActivePairings();
  }, [fetchAllActivePairings]);

  // Cleanup effect
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  return {
    allActivePairings,
    loading,
    error,
    refreshData: fetchAllActivePairings
  };
}; 