import { useState, useEffect, useCallback } from 'react'
import { getAllPairingHistoryForAdmin, type PairingDTO } from '@/config/pairingService'

interface UseAllPairingHistoryReturn {
  allPairingHistory: PairingDTO[]
  loading: boolean
  error: string | null
  refreshAllPairingHistory: () => Promise<void>
}

/**
 * Hook for fetching complete pairing history (admin only)
 * Returns all inactive pairings in the system for admin users
 */
export const useAllPairingHistory = (enabled: boolean = true): UseAllPairingHistoryReturn => {
  const [allPairingHistory, setAllPairingHistory] = useState<PairingDTO[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState<string | null>(null)

  const fetchAllPairingHistory = useCallback(async () => {
    if (!enabled) {
      setAllPairingHistory([])
      setLoading(false)
      return
    }

    try {
      setLoading(true)
      setError(null)
      
      const history = await getAllPairingHistoryForAdmin()
      setAllPairingHistory(history)
      
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || 'Failed to fetch admin pairing history'
      setError(errorMessage)
      console.error('Error fetching admin pairing history:', err)
    } finally {
      setLoading(false)
    }
  }, [enabled])

  const refreshAllPairingHistory = useCallback(async () => {
    if (enabled) {
      await fetchAllPairingHistory()
    }
  }, [fetchAllPairingHistory, enabled])

  useEffect(() => {
    fetchAllPairingHistory()
  }, [fetchAllPairingHistory])

  return {
    allPairingHistory,
    loading,
    error,
    refreshAllPairingHistory
  }
} 