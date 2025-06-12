import { useState, useEffect, useCallback, useRef } from 'react'
import { getBatchPairLevels, getBatchCurrentStreaks } from '@/config/pairingService'

interface XPDataCache {
  levels: Record<number, number>
  streaks: Record<number, number>
  lastFetched: number
  pairingIds: number[]
}

interface UseXPDataResult {
  levelData: Record<number, number>
  streakData: Record<number, number>
  loading: boolean
  error: string | null
  refetch: () => Promise<void>
}

const CACHE_DURATION = 5 * 60 * 1000 // 5 minutes
const cache = new Map<string, XPDataCache>()

/**
 * Custom hook for efficiently fetching and caching XP data (levels and streaks)
 * for multiple pairings. Implements intelligent caching to reduce API calls.
 */
export function useXPData(pairingIds: number[]): UseXPDataResult {
  const [levelData, setLevelData] = useState<Record<number, number>>({})
  const [streakData, setStreakData] = useState<Record<number, number>>({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  
  // Use ref to track previous pairingIds to avoid unnecessary fetches
  const prevPairingIdsRef = useRef<number[]>([])
  const fetchInProgressRef = useRef(false)

  // Generate cache key from sorted pairing IDs
  const cacheKey = [...pairingIds].sort().join(',')

  // Check if pairing IDs have changed
  const pairingIdsChanged = useCallback(() => {
    const prev = prevPairingIdsRef.current
    return prev.length !== pairingIds.length || 
           !pairingIds.every(id => prev.includes(id))
  }, [pairingIds])

  // Check if cache is valid
  const isCacheValid = useCallback((cacheData: XPDataCache) => {
    const now = Date.now()
    return (now - cacheData.lastFetched) < CACHE_DURATION &&
           cacheData.pairingIds.length === pairingIds.length &&
           pairingIds.every(id => cacheData.pairingIds.includes(id))
  }, [pairingIds])

  // Fetch XP data with intelligent caching
  const fetchXPData = useCallback(async (force = false) => {
    // Skip if no pairing IDs or fetch already in progress
    if (pairingIds.length === 0 || (fetchInProgressRef.current && !force)) {
      return
    }

    // Check cache first (unless forced refresh)
    if (!force) {
      const cachedData = cache.get(cacheKey)
      if (cachedData && isCacheValid(cachedData)) {
        setLevelData(cachedData.levels)
        setStreakData(cachedData.streaks)
        setError(null)
        return
      }
    }

    // Prevent concurrent fetches
    if (fetchInProgressRef.current) return
    fetchInProgressRef.current = true

    setLoading(true)
    setError(null)

    try {
      // Fetch level and streak data in parallel for optimal performance
      const [levelResponse, streakResponse] = await Promise.allSettled([
        getBatchPairLevels(pairingIds),
        getBatchCurrentStreaks(pairingIds)
      ])

      let levels: Record<number, number> = {}
      let streaks: Record<number, number> = {}

      // Process level data
      if (levelResponse.status === 'fulfilled') {
        levels = Object.entries(levelResponse.value).reduce((acc, [pairingId, levelInfo]) => {
          if (levelInfo) {
            acc[Number(pairingId)] = levelInfo.currentLevel
          }
          return acc
        }, {} as Record<number, number>)
      } else {
        console.warn('Failed to fetch level data:', levelResponse.reason)
      }

      // Process streak data
      if (streakResponse.status === 'fulfilled') {
        streaks = streakResponse.value
      } else {
        console.warn('Failed to fetch streak data:', streakResponse.reason)
      }

      // Update state
      setLevelData(levels)
      setStreakData(streaks)

      // Update cache
      cache.set(cacheKey, {
        levels,
        streaks,
        lastFetched: Date.now(),
        pairingIds: [...pairingIds]
      })

             // Clean up old cache entries (keep only last 10)
       if (cache.size > 10) {
         const oldestKey = cache.keys().next().value
         if (oldestKey) {
           cache.delete(oldestKey)
         }
       }

    } catch (err: any) {
      const errorMessage = err?.message || 'Failed to fetch XP data'
      setError(errorMessage)
      console.error('Error fetching XP data:', err)
    } finally {
      setLoading(false)
      fetchInProgressRef.current = false
    }
  }, [pairingIds, cacheKey, isCacheValid])

  // Effect to fetch data when pairing IDs change
  useEffect(() => {
    if (pairingIds.length === 0) {
      setLevelData({})
      setStreakData({})
      setError(null)
      return
    }

    // Only fetch if pairing IDs have changed or cache is invalid
    if (pairingIdsChanged()) {
      prevPairingIdsRef.current = [...pairingIds]
      fetchXPData()
    } else {
      // Check cache even if IDs haven't changed
      const cachedData = cache.get(cacheKey)
      if (cachedData && isCacheValid(cachedData)) {
        setLevelData(cachedData.levels)
        setStreakData(cachedData.streaks)
        setError(null)
      }
    }
  }, [pairingIds, fetchXPData, pairingIdsChanged, cacheKey, isCacheValid])

  // Refetch function for manual refresh
  const refetch = useCallback(async () => {
    await fetchXPData(true)
  }, [fetchXPData])

  return {
    levelData,
    streakData,
    loading,
    error,
    refetch
  }
}

/**
 * Clear the XP data cache. Useful for admin operations or when
 * you know the data has been modified externally.
 */
export function clearXPDataCache(): void {
  cache.clear()
}

/**
 * Invalidate specific pairing data from cache
 */
export function invalidateXPData(pairingIds: number[]): void {
  for (const [key, cacheData] of cache.entries()) {
    if (pairingIds.some(id => cacheData.pairingIds.includes(id))) {
      cache.delete(key)
    }
  }
} 