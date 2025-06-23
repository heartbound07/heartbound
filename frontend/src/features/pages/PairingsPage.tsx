"use client"

import type React from "react"

import { useState, useEffect, useCallback, useMemo, useRef } from "react"
import { useAuth } from "@/contexts/auth/useAuth"
import { usePairings } from "@/hooks/usePairings"
import { useAllActivePairings } from "@/hooks/useAllActivePairings"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Heart, Users, Settings, AlertCircle, Zap, UserCheck, Activity, Trash2, MessageCircle} from 'lucide-react'
import { motion, AnimatePresence } from "framer-motion"
import type { JoinQueueRequestDTO, PairingDTO } from "@/config/pairingService"
import { useQueueUpdates } from "@/contexts/QueueUpdates"
import { performMatchmaking, deleteAllPairings, enableQueue, disableQueue } from "@/config/pairingService"
import { usePairingUpdates } from "@/contexts/PairingUpdates"
import { MatchFoundModal } from "@/components/modals/MatchFoundModal"
import { UserProfileModal } from "@/components/modals/UserProfileModal"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { useXPData, invalidateXPData } from "@/hooks/useXPData"

import "@/assets/PairingsPage.css"
import { useQueueConfig } from "@/contexts/QueueConfigUpdates"
import { Skeleton } from "@/components/ui/SkeletonUI"
import { NoMatchFoundModal } from "@/components/modals/NoMatchFoundModal"
import { BreakupModal } from "@/components/modals/BreakupModal"
import { BreakupSuccessModal } from "@/components/modals/BreakupSuccessModal"
import { PartnerUnmatchedModal } from "@/components/modals/PartnerUnmatchedModal"
import { QueueStatsModal } from "@/components/modals/QueueStatsModal"
import { AdminPairManagementModal } from "@/components/modals/AdminPairManagementModal"
import { useModalManager } from "@/hooks/useModalManager"
import { useAdminQueueStats } from "@/hooks/useAdminQueueStats"
import { PairingCardList } from "./PairingCard"
import { ErrorBoundary } from "@/components/ui/ErrorBoundary"
import { XPCard } from "@/features/pages/XPCard"
import { AllMatchesModal } from "@/components/modals/AllMatchesModal"
import { useAllPairingHistory } from "@/hooks/useAllPairingHistory"

// Import extracted components
import { QueueJoinForm } from "@/features/pages/pairings/components/QueueJoinForm"
import { QueueStatus } from "@/features/pages/pairings/components/QueueStatus"
import { MatchedPairing } from "@/features/pages/pairings/components/MatchedPairing"

// Constants have been moved to individual component files

// Admin state interface
interface AdminState {
  actionLoading: boolean
  message: string | null
  queueConfigLoading: boolean
  queueConfigMessage: string | null
}

// Initial admin state
const initialAdminState: AdminState = {
  actionLoading: false,
  message: null,
  queueConfigLoading: false,
  queueConfigMessage: null,
}

// QueueJoinForm has been extracted to ./components/QueueJoinForm.tsx

export function PairingsPage() {
  const { user, hasRole } = useAuth()
  const {
    currentPairing,
    pairingHistory,
    queueStatus,
    pairedUser,
    loading,
    error,
    actionLoading,
    joinQueue,
    leaveQueue,
    refreshData,
    unpairPairing,
    deletePairing,
    clearInactiveHistory,
    breakupPairing
  } = usePairings()

  // Hook for all active pairings to display in "Current Matches" section
  const {
    allActivePairings,
    loading: allActivePairingsLoading,
    error: allActivePairingsError
  } = useAllActivePairings()
  const { isConnected } = useQueueUpdates()
  const { pairingUpdate, clearUpdate } = usePairingUpdates()

  // Use live WebSocket admin queue stats for detailed metrics
  const { queueStats, isLoading: queueStatsLoading } = useAdminQueueStats()
  
  // Use lightweight queue updates for real-time user count
  const { queueUpdate } = useQueueUpdates()

  // Admin-specific pairing history hook - only fetch for admin users
  const { 
    allPairingHistory, 
    refreshAllPairingHistory 
  } = useAllPairingHistory(hasRole("ADMIN"))

  // Use optimized modal manager
  const modalManager = useModalManager()
  const [adminState, setAdminState] = useState<AdminState>(initialAdminState)
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({})
  const [userInitiatedBreakup, setUserInitiatedBreakup] = useState(false)
  const [isInitialLoading, setIsInitialLoading] = useState(true)
  const [isAllMatchesModalOpen, setIsAllMatchesModalOpen] = useState(false)
  
  // Optimized XP data fetching with caching
  const pairingIds = useMemo(() => pairingHistory.map(p => p.id), [pairingHistory])
  const { 
    levelData: pairingLevels, 
    streakData: pairingStreaks
  } = useXPData(pairingIds)
  
  // Admin Pair Management Modal state
  const [showAdminPairModal, setShowAdminPairModal] = useState(false)
  const [selectedPairingForAdmin, setSelectedPairingForAdmin] = useState<PairingDTO | null>(null)

  // Minimum loading time in milliseconds
  const MIN_LOADING_TIME = 800



  // Queue timer state with ref for performance
  const [queueTimer, setQueueTimer] = useState<string>('0s')
  const timerIntervalRef = useRef<NodeJS.Timeout | null>(null)

  // Track the last known pairing to detect offline breakups
  const LAST_PAIRING_KEY = useMemo(() => `last-pairing-${user?.id}`, [user?.id])

  // Helper function to calculate elapsed time from backend queuedAt timestamp
  const calculateElapsedTime = useCallback((queuedAtString: string): number => {
    try {
      // Backend sends LocalDateTime without timezone info, but it's actually UTC
      // So we need to parse it as UTC, not local time
      let queuedAt: Date
      if (queuedAtString.includes('T') && !queuedAtString.includes('Z') && !queuedAtString.includes('+')) {
        // Add 'Z' to indicate UTC if not already present
        queuedAt = new Date(queuedAtString + 'Z')
      } else {
        queuedAt = new Date(queuedAtString)
      }
      
      const now = new Date()
      const timeDiff = now.getTime() - queuedAt.getTime()
      const seconds = Math.floor(timeDiff / 1000)
      
      return Math.max(0, seconds) // Ensure we don't return negative values
    } catch (error) {
      console.warn('Failed to parse queuedAt timestamp:', queuedAtString, error)
      return 0
    }
  }, [])

  // Format elapsed seconds into readable time string
  const formatElapsedTime = useCallback((totalSeconds: number): string => {
    if (totalSeconds < 0) return '0s'
    
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60

    if (hours > 0) {
      return `${hours}h ${minutes}m`
    } else if (minutes > 0) {
      return `${minutes}m ${seconds}s`
    } else {
      return `${seconds}s`
    }
  }, [])

  // Optimized queue timer effect with backend timestamp-based calculation
  useEffect(() => {
    if (queueStatus.inQueue) {
      if (queueStatus.queuedAt) {
        // Use backend timestamp when available
        const initialElapsed = calculateElapsedTime(queueStatus.queuedAt)
        setQueueTimer(formatElapsedTime(initialElapsed))
        
        // Set up interval to update timer every second
        timerIntervalRef.current = setInterval(() => {
          const currentElapsed = calculateElapsedTime(queueStatus.queuedAt!)
          setQueueTimer(formatElapsedTime(currentElapsed))
        }, 1000)
      } else {
        // Fallback: Use localStorage to track queue join time across sessions
        const queueJoinKey = `queue-join-time-${user?.id}`
        let queueStartTime = localStorage.getItem(queueJoinKey)
        
        if (!queueStartTime) {
          // First time joining queue - store current time
          queueStartTime = new Date().toISOString()
          localStorage.setItem(queueJoinKey, queueStartTime)
        }
        
        // Calculate elapsed time from stored timestamp
        const initialElapsed = calculateElapsedTime(queueStartTime)
        setQueueTimer(formatElapsedTime(initialElapsed))
        
        // Set up interval to update timer every second
        timerIntervalRef.current = setInterval(() => {
          const currentElapsed = calculateElapsedTime(queueStartTime!)
          setQueueTimer(formatElapsedTime(currentElapsed))
        }, 1000)
      }
    } else {
      // Reset timer when not in queue and clear localStorage
      setQueueTimer('0s')
      if (user?.id) {
        localStorage.removeItem(`queue-join-time-${user.id}`)
      }
      if (timerIntervalRef.current) {
        clearInterval(timerIntervalRef.current)
        timerIntervalRef.current = null
      }
    }

    return () => {
      if (timerIntervalRef.current) {
        clearInterval(timerIntervalRef.current)
        timerIntervalRef.current = null
      }
    }
  }, [queueStatus.inQueue, queueStatus.queuedAt, calculateElapsedTime, formatElapsedTime, user?.id])



  // Memoized date formatter for performance
  const formatDate = useMemo(
    () => (dateString: string) => {
      return new Intl.DateTimeFormat("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
      }).format(new Date(dateString))
    },
    [],
  )

  const { queueConfig, isQueueEnabled } = useQueueConfig()

  // Get modal functions from manager
  const {
    modalState,
    showMatchFound,
    hideMatchFound,
    showUserProfile,
    hideUserProfile,
    showNoMatch,
    hideNoMatch,
    showQueueRemoved,
    hideQueueRemoved,
    showBreakup,
    hideBreakup,
    showBreakupSuccess,
    hideBreakupSuccess,
    showPartnerUnmatched,
    hidePartnerUnmatched,
    showQueueStats,
    hideQueueStats,
  } = modalManager

  const updateAdminState = useCallback((updates: Partial<AdminState>) => {
    setAdminState(prev => ({ ...prev, ...updates }))
  }, [])

  // Secure form validation with sanitization
  const handleJoinQueue = useCallback(
    async (queueData: Omit<JoinQueueRequestDTO, "userId">) => {
      if (!user?.id) {
        throw new Error("User authentication required")
      }

      // Additional security validation
      const sanitizedData = {
        ...queueData,
        userId: user.id,
        age: Math.max(15, Math.min(100, queueData.age)), // Clamp age values
      }

      try {
        await joinQueue(sanitizedData)
      } catch (err: any) {
        const errorMessage = err?.message || "Failed to join matchmaking queue"
        console.error("Queue join error:", err)
        throw new Error(errorMessage)
      }
    },
    [user?.id, joinQueue],
  )

  // Admin functions with enhanced error handling
  const handleAdminMatchmaking = useCallback(async () => {
    try {
      updateAdminState({ actionLoading: true, message: null })

      const newPairings = await performMatchmaking()
      updateAdminState({ 
        message: `Successfully created ${newPairings.length} new pairings! Notifications will be sent shortly...` 
      })
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || "Matchmaking failed"
      updateAdminState({ message: `Error: ${errorMessage}` })
      console.error("Admin matchmaking error:", err)
    } finally {
      updateAdminState({ actionLoading: false })
    }
  }, [updateAdminState])

  const handleDeleteAllPairings = useCallback(async () => {
    if (!confirm("Are you sure you want to delete ALL active pairings? This action cannot be undone.")) {
      return
    }

    try {
      updateAdminState({ actionLoading: true, message: null })

      const result = await deleteAllPairings()
      updateAdminState({ 
        message: `Successfully deleted ${result.deletedCount} active pairing(s)!` 
      })

      window.location.reload()
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      updateAdminState({ message: `Failed to delete pairings: ${error.message}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } finally {
      updateAdminState({ actionLoading: false })
    }
  }, [updateAdminState])



  const handleShowQueueStats = useCallback(() => {
    showQueueStats()
  }, [showQueueStats])

  // Admin Pair Management Modal handlers
  const handleOpenAdminPairModal = useCallback((pairing: PairingDTO) => {
    setSelectedPairingForAdmin(pairing)
    setShowAdminPairModal(true)
  }, [])

  const handleCloseAdminPairModal = useCallback(async () => {
    setShowAdminPairModal(false)
    setSelectedPairingForAdmin(null)
    // Refresh data after closing modal to show any updates
    refreshData()
    
    // Refresh admin pairing history if user is admin
    if (hasRole("ADMIN")) {
      refreshAllPairingHistory()
    }
    
    // Invalidate XP cache to trigger fresh data fetch through useXPData hook
    if (pairingHistory.length > 0) {
      const pairingIds = pairingHistory.map(p => p.id)
      invalidateXPData(pairingIds)
    }
  }, [refreshData, hasRole, refreshAllPairingHistory, pairingHistory])

  // Effect to handle real-time WebSocket updates
  useEffect(() => {
    if (pairingUpdate) {
      if (pairingUpdate.eventType === "MATCH_FOUND" && pairingUpdate.pairing) {
        console.log("[PairingsPage] Match found, showing modal:", pairingUpdate)
        showMatchFound(pairingUpdate.pairing)
        refreshData()
        clearUpdate()
      } else if (pairingUpdate.eventType === "NO_MATCH_FOUND") {
        console.log("[PairingsPage] No match found, showing modal:", pairingUpdate)
        showNoMatch({
          totalInQueue: pairingUpdate.totalInQueue,
          message: pairingUpdate.message
        })
        clearUpdate()
      } else if (pairingUpdate.eventType === "QUEUE_REMOVED") {
        console.log("[PairingsPage] Removed from queue by admin:", pairingUpdate)
        showQueueRemoved(pairingUpdate.message)
        refreshData() // This is crucial to update the UI state
        clearUpdate()
      } else if (pairingUpdate.eventType === "PAIRING_ENDED") {
        console.log("[PairingsPage] Pairing ended:", pairingUpdate)
        if (pairingUpdate.isInitiator) {
          // User initiated the breakup - show success modal
          setUserInitiatedBreakup(true) // Mark that user initiated this breakup
          showBreakupSuccess()
        } else {
          // Partner initiated the breakup - show partner unmatched modal
          showPartnerUnmatched()
        }
        refreshData() // Update pairing data
        
        // Refresh admin pairing history if user is admin (new pairing became inactive)
        if (hasRole("ADMIN")) {
          refreshAllPairingHistory()
        }
        
        clearUpdate()
      } else if (pairingUpdate.eventType === "ACTIVITY_UPDATE") {
        console.log("[PairingsPage] Activity update received - handled by usePairings hook:", pairingUpdate)
        // Activity updates are handled by the usePairings hook for optimal performance
        // No need to call refreshData() here as it would cause unnecessary re-fetching
        // The hook already updates currentPairing state, which will trigger UI re-renders
      }
    }
  }, [pairingUpdate, refreshData, hasRole, refreshAllPairingHistory, clearUpdate, showMatchFound, showNoMatch, showQueueRemoved, showBreakupSuccess, showPartnerUnmatched])

  // Effect to track current pairing in localStorage for offline breakup detection
  useEffect(() => {
    if (!user?.id) return

    const lastPairingData = localStorage.getItem(LAST_PAIRING_KEY)
    const lastPairing = lastPairingData ? JSON.parse(lastPairingData) : null

    if (currentPairing) {
      // User has an active pairing - store it
      localStorage.setItem(LAST_PAIRING_KEY, JSON.stringify({
        id: currentPairing.id,
        partnerId: currentPairing.user1Id === user.id ? currentPairing.user2Id : currentPairing.user1Id,
        partnerName: pairedUser?.displayName || "your match",
        timestamp: Date.now()
      }))
    } else if (lastPairing && !loading && !userInitiatedBreakup) {
      // User had a pairing but now doesn't AND they didn't initiate the breakup themselves
      // This means they were unmatched while offline
      console.log("[PairingsPage] Detected offline breakup, showing partner unmatched modal")
      showPartnerUnmatched(lastPairing.partnerName || "your match")
      
      // Clear the stored pairing since we've shown the notification
      localStorage.removeItem(LAST_PAIRING_KEY)
    } else if (!currentPairing && userInitiatedBreakup) {
      // User initiated breakup and pairing is now gone - clean up the flag and localStorage
      console.log("[PairingsPage] User-initiated breakup completed, cleaning up")
      setUserInitiatedBreakup(false)
      localStorage.removeItem(LAST_PAIRING_KEY)
    }
  }, [currentPairing, user?.id, pairedUser?.displayName, loading, LAST_PAIRING_KEY, userInitiatedBreakup, showPartnerUnmatched])

  useEffect(() => {
    const fetchUserProfiles = async () => {
      const userIds = new Set<string>()

      // Add user IDs from current user's pairing history
      pairingHistory.forEach((pairing) => {
        userIds.add(pairing.user1Id)
        userIds.add(pairing.user2Id)
      })

      // Add user IDs from all active pairings (for "Current Matches" display)
      allActivePairings?.forEach((pairing) => {
        userIds.add(pairing.user1Id)
        userIds.add(pairing.user2Id)
      })

      // Add user IDs from admin pairing history (for "Match History" display)
      if (hasRole("ADMIN") && allPairingHistory) {
        allPairingHistory.forEach((pairing) => {
          userIds.add(pairing.user1Id)
          userIds.add(pairing.user2Id)
        })
      }

      if (userIds.size > 0) {
        try {
          const profiles = await getUserProfiles(Array.from(userIds))
          setUserProfiles(profiles)
        } catch (error) {
          console.error("Failed to fetch user profiles:", error)
        }
      }
    }

    if (pairingHistory.length > 0 || allActivePairings?.length > 0 || (hasRole("ADMIN") && allPairingHistory?.length > 0)) {
      fetchUserProfiles()
    }
  }, [pairingHistory, allActivePairings, hasRole, allPairingHistory])

  // Handle initial loading with minimum loading time
  useEffect(() => {
    if (!loading && isInitialLoading) {
      const timer = setTimeout(() => {
        setIsInitialLoading(false);
      }, MIN_LOADING_TIME);
      
      return () => clearTimeout(timer);
    }
  }, [loading, isInitialLoading, MIN_LOADING_TIME])



  const handleUserClick = useCallback(
    (userId: string, event: React.MouseEvent) => {
      const profile = userProfiles[userId]
      if (profile) {
        showUserProfile(profile, { x: event.clientX, y: event.clientY })
      }
    },
    [userProfiles, showUserProfile],
  )

  const handleCloseUserProfileModal = useCallback(() => {
    hideUserProfile()
  }, [hideUserProfile])

  const handleCloseMatchModal = useCallback(() => {
    console.log("[PairingsPage] Closing match modal")
    hideMatchFound()
  }, [hideMatchFound])

  const handleEnableQueue = async () => {
    try {
      updateAdminState({ queueConfigLoading: true, queueConfigMessage: null })

      const result = await enableQueue()
      updateAdminState({ 
        queueConfigMessage: `Queue enabled successfully: ${result.message}` 
      })

      setTimeout(() => updateAdminState({ queueConfigMessage: null }), 5000)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || "Failed to enable queue"
      updateAdminState({ queueConfigMessage: `Error: ${errorMessage}` })
      setTimeout(() => updateAdminState({ queueConfigMessage: null }), 5000)
    } finally {
      updateAdminState({ queueConfigLoading: false })
    }
  }

  const handleDisableQueue = async () => {
    if (
      !confirm(
        "Are you sure you want to disable the matchmaking queue? Users will not be able to join until re-enabled.",
      )
    ) {
      return
    }

    try {
      updateAdminState({ queueConfigLoading: true, queueConfigMessage: null })

      const result = await disableQueue()
      updateAdminState({ 
        queueConfigMessage: `Queue disabled successfully: ${result.message}` 
      })

      setTimeout(() => updateAdminState({ queueConfigMessage: null }), 5000)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || "Failed to disable queue"
      updateAdminState({ queueConfigMessage: `Error: ${errorMessage}` })
      setTimeout(() => updateAdminState({ queueConfigMessage: null }), 5000)
    } finally {
      updateAdminState({ queueConfigLoading: false })
    }
  }

  const handleCloseNoMatchModal = useCallback(() => {
    console.log("[PairingsPage] Closing no match modal")
    hideNoMatch()
  }, [hideNoMatch])

  const handleStayInQueue = () => {
    console.log("[PairingsPage] User chose to stay in queue")
    // User is already in queue, just close modal
    handleCloseNoMatchModal()
  }

  const handleLeaveQueueFromModal = async () => {
    console.log("[PairingsPage] User chose to leave queue from modal")
    try {
      await leaveQueue()
      handleCloseNoMatchModal()
    } catch (error) {
      console.error("Failed to leave queue:", error)
      // Still close modal even if leave queue fails
      handleCloseNoMatchModal()
    }
  }

  const handleUnpairUsers = async (pairingId: number, event: React.MouseEvent) => {
    event.stopPropagation();
    
    if (!confirm("Are you sure you want to unpair these users? This will end their active match but they will remain blacklisted from matching again. This action cannot be undone.")) {
      return;
    }

    try {
      await unpairPairing(pairingId);
      updateAdminState({ message: "Users unpaired successfully! They remain blacklisted from future matches." })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      updateAdminState({ message: `Failed to unpair users: ${error.message}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    }
  };

  const handleDeletePairing = async (pairingId: number, event: React.MouseEvent) => {
    event.stopPropagation();
    
    if (!confirm("Are you sure you want to permanently delete this pairing record? This will also remove the blacklist entry, allowing these users to match again. This action cannot be undone.")) {
      return;
    }

    try {
      await deletePairing(pairingId);
      updateAdminState({ message: "Pairing record permanently deleted! Users can now match again." })
      
      // Refresh admin pairing history to reflect deletion
      if (hasRole("ADMIN")) {
        refreshAllPairingHistory()
      }
      
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      updateAdminState({ message: `Failed to delete pairing: ${error.message}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    }
  };

  const handleClearInactiveHistory = async () => {
    if (!confirm("Are you sure you want to permanently delete ALL inactive pairing records? This will also remove all blacklist entries, allowing users to match again. This action cannot be undone.")) {
      return;
    }

    try {
      const result = await clearInactiveHistory();
      updateAdminState({ message: `Successfully deleted ${result.deletedCount} inactive pairing record(s)! All users can now match again.` })
      
      // Refresh admin pairing history to reflect deletions
      if (hasRole("ADMIN")) {
        refreshAllPairingHistory()
      }
      
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      updateAdminState({ message: `Failed to clear inactive history: ${error.message}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    }
  };

  // Filter pairings into current matches and history
  const currentMatches = useMemo(() => {
    // Use all active pairings from the system, not just user-specific ones
    return allActivePairings || []
  }, [allActivePairings])

  const inactiveHistory = useMemo(() => {
    // Admin users see all inactive pairings, regular users see only their own
    if (hasRole("ADMIN")) {
      return allPairingHistory || []
    } else {
      return pairingHistory.filter(pairing => !pairing.active)
    }
  }, [hasRole, allPairingHistory, pairingHistory])

  const handleCloseQueueRemovedModal = useCallback(() => {
    hideQueueRemoved()
  }, [hideQueueRemoved])

  const handleBreakup = useCallback(async (reason: string) => {
    if (!currentPairing || !user?.id) return
    
    try {
      await breakupPairing(currentPairing.id, reason)
      hideBreakup()
      // Success modal will be shown via WebSocket event
    } catch (error) {
      console.error("Error processing breakup:", error)
      // Error is already handled by the hook and displayed in the UI
    }
  }, [currentPairing, user?.id, breakupPairing, hideBreakup])

  const handleCloseBreakupSuccessModal = useCallback(() => {
    hideBreakupSuccess()
    // Don't clear userInitiatedBreakup flag here - let the effect handle cleanup
  }, [hideBreakupSuccess])

  const handleClosePartnerUnmatchedModal = useCallback(() => {
    hidePartnerUnmatched()
    
    // Also clear any remaining stored pairing data when closing the modal
    if (user?.id) {
      localStorage.removeItem(LAST_PAIRING_KEY)
    }
  }, [user?.id, LAST_PAIRING_KEY, hidePartnerUnmatched])
  
  const handleJoinQueueFromPartnerModal = useCallback(() => {
    // This could trigger the queue join form or just close and let user use main form
    hidePartnerUnmatched()
    // Could add logic here to auto-open queue join form if needed
  }, [hidePartnerUnmatched])

  if (loading || isInitialLoading) {
          return (
        <div className="min-h-screen bg-theme-gradient">
            <div className="min-h-screen bg-theme-gradient">
              <div className="container mx-auto px-4 py-8 max-w-7xl">
                {/* Admin Controls Skeleton */}
                {hasRole("ADMIN") && (
                  <div className="mb-8">
                    <div className="valorant-card">
                      <div className="p-6 border-b border-theme">
                        <div className="flex items-center gap-3 mb-4">
                          <Skeleton variant="circular" width="32px" height="32px" theme="valorant" />
                          <Skeleton width="150px" height="24px" theme="valorant" />
                        </div>
                      </div>
                      <div className="p-6 space-y-6">
                        {/* Queue Status Skeleton */}
                        <div className="p-4 rounded-xl bg-theme-container border-theme">
                          <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-3">
                              <Skeleton variant="circular" width="16px" height="16px" theme="valorant" />
                              <Skeleton width="200px" height="20px" theme="valorant" />
                            </div>
                            <Skeleton width="80px" height="20px" borderRadius="9999px" theme="valorant" />
                          </div>
                          <Skeleton width="60%" height="16px" theme="valorant" />
                        </div>
                        
                        {/* Admin Buttons Skeleton */}
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                          {[1, 2, 3, 4].map((i) => (
                            <Skeleton key={i} width="100%" height="48px" borderRadius="6px" theme="valorant" />
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                )}
                
                {/* Hero Section - Shows during loading for better UX */}
                <motion.div className="section-header mb-12 text-center">
                  <motion.h1 
                    initial={{ scale: 0.9, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={{ delay: 0.1, type: "spring" }}
                    className="pairings-hero-title font-grandstander text-4xl md:text-5xl text-primary mb-4"
                  >
                    Pairings
                  </motion.h1>
                  
                  <motion.p 
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.2 }}
                    className="text-xl text-theme-secondary max-w-2xl mx-auto leading-relaxed"
                  >
                    {/* Loading state message */}
                  </motion.p>
                </motion.div>

                {/* Main Content Grid Skeleton */}
                <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
                  {/* Left Column - Main Content */}
                  <div className="xl:col-span-2 space-y-8">
                    {/* Primary Card Skeleton */}
                    <div className="valorant-card">
                      <div className="p-6 border-b border-theme">
                        <div className="flex items-center gap-3">
                          <Skeleton variant="circular" width="24px" height="24px" theme="valorant" />
                          <Skeleton width="180px" height="24px" theme="valorant" />
                        </div>
                      </div>
                      <div className="p-8 space-y-6">
                        {/* Main content area */}
                        <div className="text-center space-y-4">
                          <Skeleton width="120px" height="32px" borderRadius="9999px" className="mx-auto" theme="valorant" />
                          <Skeleton width="250px" height="32px" className="mx-auto" theme="valorant" />
                          <Skeleton width="150px" height="16px" className="mx-auto" theme="valorant" />
                        </div>
                        
                        {/* Form-like content */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                          <div className="space-y-2">
                            <Skeleton width="60px" height="16px" theme="valorant" />
                            <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                          </div>
                          <div className="space-y-2">
                            <Skeleton width="80px" height="16px" theme="valorant" />
                            <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                          </div>
                        </div>
                        
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div className="space-y-2">
                            <Skeleton width="60px" height="16px" theme="valorant" />
                            <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                          </div>
                          <div className="space-y-2">
                            <Skeleton width="100px" height="16px" theme="valorant" />
                            <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                          </div>
                        </div>
                        
                        <Skeleton width="100%" height="48px" borderRadius="6px" theme="valorant" />
                      </div>
                    </div>


                  </div>

                  {/* Right Column */}
                  <div className="xl:col-span-1 space-y-8">
                    {/* XP Card Skeleton */}
                    <div className="valorant-card h-fit">
                      <div className="p-6 border-b border-theme">
                        <div className="flex items-center gap-3 mb-4">
                          <Skeleton variant="circular" width="24px" height="24px" theme="valorant" />
                          <Skeleton width="200px" height="24px" theme="valorant" />
                        </div>
                        
                        {/* Tab Navigation Skeleton */}
                        <div className="flex gap-2">
                          {[1, 2, 3].map((i) => (
                            <Skeleton key={i} width="80px" height="32px" borderRadius="6px" theme="valorant" />
                          ))}
                        </div>
                      </div>
                      <div className="p-6 space-y-6">
                        {/* Level Progress Skeleton */}
                        <div className="space-y-4">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <Skeleton variant="circular" width="48px" height="48px" theme="valorant" />
                              <div className="space-y-2">
                                <Skeleton width="100px" height="20px" theme="valorant" />
                                <Skeleton width="80px" height="16px" theme="valorant" />
                              </div>
                            </div>
                            <Skeleton width="120px" height="24px" borderRadius="9999px" theme="valorant" />
                          </div>
                          
                          {/* Progress Bar Skeleton */}
                          <div className="space-y-2">
                            <div className="flex justify-between">
                              <Skeleton width="100px" height="14px" theme="valorant" />
                              <Skeleton width="40px" height="14px" theme="valorant" />
                            </div>
                            <Skeleton width="100%" height="12px" borderRadius="9999px" theme="valorant" />
                          </div>
                        </div>
                        
                        {/* Quick Stats Grid Skeleton */}
                        <div className="grid grid-cols-2 gap-4">
                          {[1, 2].map((i) => (
                            <div key={i} className="p-4 rounded-lg bg-theme-container">
                              <div className="flex items-center gap-2 mb-2">
                                <Skeleton variant="circular" width="16px" height="16px" theme="valorant" />
                                <Skeleton width="80px" height="14px" theme="valorant" />
                              </div>
                              <Skeleton width="40px" height="24px" theme="valorant" />
                            </div>
                          ))}
                        </div>
                        
                        {/* Recent Items Skeleton */}
                        <div className="space-y-3">
                          <div className="flex items-center gap-2">
                            <Skeleton variant="circular" width="16px" height="16px" theme="valorant" />
                            <Skeleton width="120px" height="16px" theme="valorant" />
                          </div>
                          <div className="space-y-2">
                            {[1, 2].map((i) => (
                              <div key={i} className="p-3 rounded-lg bg-theme-container">
                                <div className="flex items-center justify-between">
                                  <div className="flex-1">
                                    <Skeleton width="70%" height="16px" className="mb-1" theme="valorant" />
                                    <Skeleton width="50%" height="12px" theme="valorant" />
                                  </div>
                                  <Skeleton width="60px" height="20px" borderRadius="9999px" theme="valorant" />
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Current Matches */}
                    <div className="valorant-card h-fit">
                      <div className="p-6 border-b border-theme">
                        <div className="flex items-center gap-3">
                          <Skeleton variant="circular" width="24px" height="24px" theme="valorant" />
                          <Skeleton width="120px" height="24px" theme="valorant" />
                          <Skeleton width="30px" height="20px" borderRadius="9999px" theme="valorant" />
                        </div>
                      </div>
                      <div className="p-6 space-y-4">
                        {[1, 2, 3].map((i) => (
                          <div key={i} className="flex items-center justify-between p-4 rounded-lg bg-theme-container">
                            <div className="flex items-center gap-4">
                              <Skeleton variant="circular" width="40px" height="40px" theme="valorant" />
                              <div className="space-y-2">
                                <Skeleton width="100px" height="16px" theme="valorant" />
                                <Skeleton width="80px" height="14px" theme="valorant" />
                              </div>
                            </div>
                            <div className="flex gap-2">
                              <Skeleton width="60px" height="20px" borderRadius="9999px" theme="valorant" />
                              <Skeleton width="80px" height="20px" borderRadius="9999px" theme="valorant" />
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Match History for Admin */}
                    {hasRole("ADMIN") && (
                      <div className="valorant-card h-fit">
                        <div className="p-6 border-b border-theme">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <Skeleton variant="circular" width="24px" height="24px" theme="valorant" />
                              <Skeleton width="120px" height="24px" theme="valorant" />
                              <Skeleton width="30px" height="20px" borderRadius="9999px" theme="valorant" />
                            </div>
                            <Skeleton width="100px" height="32px" borderRadius="6px" theme="valorant" />
                          </div>
                        </div>
                        <div className="p-6 space-y-4">
                          {[1, 2].map((i) => (
                            <div key={i} className="flex items-center justify-between p-4 rounded-lg bg-theme-container">
                              <div className="flex items-center gap-4">
                                <Skeleton variant="circular" width="40px" height="40px" theme="valorant" />
                                <div className="space-y-2">
                                  <Skeleton width="100px" height="16px" theme="valorant" />
                                  <Skeleton width="80px" height="14px" theme="valorant" />
                                </div>
                              </div>
                              <Skeleton width="80px" height="20px" borderRadius="9999px" theme="valorant" />
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
        </div>
      )
  }

  return (
    <ErrorBoundary>
      <div className="pairings-page-wrapper">
        <div className="pairings-inner-container">
            {/* Admin Controls */}
            <AnimatePresence>
              {hasRole("ADMIN") && (
                <div className="mb-8">
                  <Card className="admin-controls">
                    <CardHeader className="pb-4">
                      <CardTitle className="flex items-center gap-3 text-white">
                        <div className="p-2 bg-primary/20 rounded-lg">
                          <Settings className="h-5 w-5 text-primary" />
                        </div>
                        Admin Dashboard
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-6">
                      {/* Queue Status Display */}
                      <div className="p-4 rounded-xl border-theme bg-theme-container backdrop-blur-sm theme-transition">
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-3">
                            <div
                              className={`w-4 h-4 rounded-full ${
                                isQueueEnabled ? "bg-status-success" : "bg-status-error"
                              }`}
                            />
                            <span className="text-white font-semibold text-lg">
                              Queue Status: {isQueueEnabled ? "Active" : "Disabled"}
                            </span>
                          </div>
                          {queueConfig && (
                            <Badge variant="outline" className="text-xs border-theme">
                              Updated by {queueConfig.updatedBy}
                            </Badge>
                          )}
                        </div>
                        {queueConfig && (
                          <p className="text-theme-secondary text-sm">{queueConfig.message}</p>
                        )}
                      </div>

                      {/* Admin Action Buttons */}
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        <div>
                          <Button
                            onClick={handleEnableQueue}
                            disabled={adminState.queueConfigLoading || isQueueEnabled}
                            variant={isQueueEnabled ? "outline" : "default"}
                            className="w-full h-12 valorant-button-success"
                          >
                            {adminState.queueConfigLoading ? (
                              <Skeleton width="80px" height="16px" theme="valorant" className="mx-auto" />
                            ) : (
                              <>
                                <Activity className="h-4 w-4 mr-2" />
                                Enable Queue
                              </>
                            )}
                          </Button>
                        </div>

                        <div>
                          <Button
                            onClick={handleDisableQueue}
                            disabled={adminState.queueConfigLoading || !isQueueEnabled}
                            variant="destructive"
                            className="w-full h-12"
                          >
                            {adminState.queueConfigLoading ? (
                              <Skeleton width="80px" height="16px" theme="valorant" className="mx-auto" />
                            ) : (
                              <>
                                <AlertCircle className="h-4 w-4 mr-2" />
                                Disable Queue
                              </>
                            )}
                          </Button>
                        </div>

                        <div>
                          <Button
                            onClick={handleAdminMatchmaking}
                            disabled={adminState.actionLoading}
                            className="w-full h-12 valorant-button-primary"
                          >
                            {adminState.actionLoading ? (
                              <Skeleton width="100px" height="16px" theme="valorant" className="mx-auto" />
                            ) : (
                              <>
                                <Zap className="h-4 w-4 mr-2" />
                                Run Matchmaking
                              </>
                            )}
                          </Button>
                        </div>

                        <div>
                          <Button
                            onClick={handleDeleteAllPairings}
                            disabled={adminState.actionLoading}
                            variant="destructive"
                            className="w-full h-12"
                          >
                            {adminState.actionLoading ? (
                              <Skeleton width="100px" height="16px" theme="valorant" className="mx-auto" />
                            ) : (
                              <>
                                <Users className="h-4 w-4 mr-2" />
                                Clear All Pairings
                              </>
                            )}
                          </Button>
                        </div>
                      </div>

                      {/* Admin Messages */}
                      {(adminState.message || adminState.queueConfigMessage) && (
                        <div
                          className={`p-4 rounded-xl text-sm font-medium ${
                            (adminState.message || adminState.queueConfigMessage)?.includes("Error")
                              ? "bg-status-error/10 border border-status-error/20 text-status-error"
                              : "bg-status-success/10 border border-status-success/20 text-status-success"
                          }`}
                        >
                          {adminState.queueConfigMessage || adminState.message}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                </div>
              )}
            </AnimatePresence>

            {/* Hero Section - Always visible in main render */}
            <motion.div className="section-header mb-12 text-center">
              <motion.h1 
                initial={{ scale: 0.9, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: 0.1, type: "spring" }}
                className="pairings-hero-title font-grandstander text-4xl md:text-5xl text-primary mb-4"
              >
                Pairings
              </motion.h1>
              
              <motion.p 
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.2 }}
                className="text-xl text-theme-secondary max-w-2xl mx-auto leading-relaxed"
              >
              </motion.p>
            </motion.div>

            {/* Error Display */}
            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  className="mb-8"
                >
                  <div className="rounded-xl p-6 backdrop-blur-sm bg-theme-container/30 border border-status-error/20 theme-transition">
                    <div className="flex items-center gap-3">
                      <AlertCircle className="h-6 w-6 text-status-error" />
                      <p className="text-status-error font-medium">{error}</p>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Main Content Grid */}
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-4 sm:gap-6 lg:gap-8 w-full max-w-full overflow-hidden">
              {/* Left Column - Current Status & Queue */}
              <div className="xl:col-span-2 space-y-4 sm:space-y-6 lg:space-y-8">
                {/* Current Status */}
                <AnimatePresence mode="wait">
                  {currentPairing ? (
                    <MatchedPairing
                      currentPairing={currentPairing}
                      pairedUser={pairedUser}
                      user={user}
                      actionLoading={actionLoading}
                      onUserClick={handleUserClick}
                      onBreakup={() => showBreakup()}
                      formatDate={formatDate}
                    />
                  ) : queueStatus.inQueue ? (
                    <QueueStatus
                      queueStatus={queueStatus}
                      queueTimer={queueTimer}
                      isConnected={isConnected}
                      queueUpdate={queueUpdate}
                      actionLoading={actionLoading}
                      onLeaveQueue={leaveQueue}
                    />
                  ) : null}
                </AnimatePresence>

                {/* Admin Queue Statistics Display */}
                {hasRole("ADMIN") && !currentPairing && (
                  <div className="mb-6">
                      <Card className="valorant-card">
                        <CardHeader className="pb-4">
                          <div className="flex items-center justify-between">
                            <CardTitle className="flex items-center gap-3 text-white">
                              <div className="p-2 bg-status-info/20 rounded-lg">
                                <Activity className="h-5 w-5 text-status-info" />
                              </div>
                              Queue Statistics
                            </CardTitle>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={handleShowQueueStats}
                              className="border-primary/30 text-primary hover:border-primary/50 hover:bg-primary/10"
                            >
                              <Activity className="h-4 w-4 mr-2" />
                              View Details
                            </Button>
                          </div>
                        </CardHeader>
                        <CardContent>
                          {queueStatsLoading ? (
                            <div className="flex justify-center">
                              <div className="text-center p-4 bg-theme-container rounded-lg max-w-xs w-full">
                                <Skeleton width="40px" height="32px" className="mx-auto mb-2" theme="valorant" />
                                <Skeleton width="80px" height="16px" className="mx-auto" theme="valorant" />
                              </div>
                            </div>
                          ) : (
                            <div className="flex justify-center">
                              {/* Simplified queue statistics - only showing user count */}
                              <div className="text-center p-6 bg-theme-container rounded-lg border border-primary/20 max-w-xs w-full">
                                <div className="text-3xl font-bold text-primary mb-2">
                                  {queueUpdate?.totalQueueSize ?? queueStats?.totalUsersInQueue ?? 0}
                                </div>
                                <div className="text-base text-theme-secondary mb-1">Users in Queue</div>
                                <div className="text-xs text-theme-tertiary">
                                  {queueUpdate?.totalQueueSize !== undefined ? '● Live' : '○ Cached'}
                                </div>
                              </div>
                            </div>
                          )}
                        </CardContent>
                      </Card>
                    </div>
                )}

                {/* Queue Join Section */}
                {!currentPairing && (
                  <ErrorBoundary>
                    <AnimatePresence mode="wait">
                      {isQueueEnabled ? (
                        currentPairing ? (
                          <motion.div
                            key="already-matched"
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -20 }}
                          >
                            <Card className="valorant-card">
                              <CardContent className="text-center py-8">
                                <Heart className="h-12 w-12 text-primary mx-auto mb-4" />
                                <h3 className="text-xl font-bold text-white mb-2">You're All Set!</h3>
                                <p className="text-theme-secondary">
                                  Check your Discord for your private channel and start chatting!
                                </p>
                              </CardContent>
                            </Card>
                          </motion.div>
                        ) : !queueStatus.inQueue ? (
                          <QueueJoinForm onJoinQueue={handleJoinQueue} loading={actionLoading} />
                        ) : null
                      ) : (
                        <motion.div
                          key="queue-disabled"
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -20 }}
                        >
                          <Card className="valorant-card">
                            <CardContent className="text-center py-12">
                              <motion.div
                                animate={{ rotate: [0, 10, -10, 0] }}
                                transition={{ duration: 2, repeat: Number.POSITIVE_INFINITY }}
                              >
                                <AlertCircle className="h-16 w-16 text-status-warning mx-auto mb-6" />
                              </motion.div>
                              <h3 className="text-2xl font-bold text-status-warning mb-4">Queue Closed</h3>
                              <p className="text-theme-secondary text-lg">
                                The matchmaking queue is finished. Check back next week to start matching!
                              </p>
                            </CardContent>
                          </Card>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </ErrorBoundary>
                )}
              </div>

              {/* Right Column - XP Card, Current Matches & Match History */}
              <motion.div 
                className="xl:col-span-1 space-y-4 sm:space-y-6 lg:space-y-8"
                layout
                transition={{ duration: 0.3, ease: "easeInOut" }}
              >
                {/* XP Card - Show XP, Achievements, and Voice Streaks for Active Pairing */}
                <AnimatePresence>
                  {currentPairing && (
                    <motion.div
                      key="xp-card-right"
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 20 }}
                      transition={{ duration: 0.5, delay: 0.2 }}
                      layout
                    >
                      <XPCard 
                        pairingId={currentPairing.id}
                        className=""
                      />
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Current Matches - Visible to All Users */}
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.3 }}
                  layout
                >
                  <Card className="valorant-card h-fit">
                    <CardHeader className="pb-4">
                      <div className="flex items-center justify-between">
                        <CardTitle className="flex items-center gap-3 text-white">
                          <div className="p-2 bg-status-success/20 rounded-lg">
                            <UserCheck className="h-5 w-5 text-status-success" />
                          </div>
                          Current Matches
                          <Badge variant="outline" className="">
                            {currentMatches.length}
                          </Badge>
                        </CardTitle>
                        <Button variant="outline" size="sm" onClick={() => setIsAllMatchesModalOpen(true)}>
                          View all
                        </Button>
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <PairingCardList
                        pairings={currentMatches}
                        userProfiles={userProfiles}
                        isActive={true}
                        onUserClick={handleUserClick}
                        onUnpair={handleUnpairUsers}
                        onManagePair={hasRole("ADMIN") ? handleOpenAdminPairModal : undefined}
                        formatDate={formatDate}
                        hasAdminActions={hasRole("ADMIN")}
                        maxItems={10}
                        emptyMessage="No Active Matches"
                        emptyIcon={
                          <div className="mx-auto w-16 h-16 bg-gradient-to-br from-status-success/20 to-primary/20 rounded-full flex items-center justify-center mb-4">
                            <UserCheck className="h-8 w-8 text-status-success" />
                          </div>
                        }
                        streakData={pairingStreaks}
                        levelData={pairingLevels}
                      />
                      {allActivePairingsLoading && (
                        <motion.div
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-center"
                        >
                          <Skeleton width="100%" height="60px" theme="valorant" />
                        </motion.div>
                      )}
                      {allActivePairingsError && (
                        <motion.div
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-center"
                        >
                          <p className="text-status-error text-sm">
                            Failed to load active matches: {allActivePairingsError}
                          </p>
                        </motion.div>
                      )}
                      {!allActivePairingsLoading && !allActivePairingsError && currentMatches.length === 0 && !currentPairing && !queueStatus.inQueue && isQueueEnabled && (
                        <motion.div
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-center"
                        >
                          <p className="text-theme-secondary text-sm mb-2">
                            There are currently no active matches in the system.
                          </p>
                          <p className="text-theme-tertiary text-xs">
                            Join the queue to find your match!
                          </p>
                        </motion.div>
                      )}
                    </CardContent>
                  </Card>
                </motion.div>

                {/* Match History - Admin Only */}
                <AnimatePresence>
                  {hasRole("ADMIN") && (
                    <motion.div
                      initial={{ opacity: 0, x: 20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 20 }}
                      transition={{ delay: 0.4 }}
                      layout
                    >
                      <Card className="valorant-card h-fit">
                        <CardHeader className="pb-4">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-3">
                              <div className="p-2 bg-primary/20 rounded-lg">
                                <MessageCircle className="h-5 w-5 text-primary" />
                              </div>
                              <CardTitle className="text-white">Match History</CardTitle>
                              <Badge variant="outline" className="">
                                {inactiveHistory.length}
                              </Badge>
                            </div>
                            
                            {/* Admin Clear History Button */}
                            {inactiveHistory.length > 0 && (
                              <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                                <Button
                                  onClick={handleClearInactiveHistory}
                                  disabled={actionLoading}
                                  variant="outline"
                                  size="sm"
                                  className="border-status-error/30 text-status-error hover:border-status-error/50 hover:bg-status-error/10"
                                >
                                  {actionLoading ? (
                                    <Skeleton width="80px" height="16px" theme="valorant" className="mx-auto" />
                                  ) : (
                                    <>
                                      <Trash2 className="h-4 w-4 mr-1" />
                                      Clear History
                                    </>
                                  )}
                                </Button>
                              </motion.div>
                            )}
                          </div>
                        </CardHeader>
                        <CardContent className="space-y-4">
                          <PairingCardList
                            pairings={inactiveHistory}
                            userProfiles={userProfiles}
                            isActive={false}
                            onUserClick={handleUserClick}
                            onDelete={handleDeletePairing}
                            onManagePair={handleOpenAdminPairModal}
                            formatDate={formatDate}
                            hasAdminActions={true}
                            maxItems={5}
                            emptyMessage="No Match History"
                            emptyIcon={
                              <div className="mx-auto w-16 h-16 bg-gradient-to-br from-primary/20 to-purple-500/20 rounded-full flex items-center justify-center mb-4">
                                <MessageCircle className="h-8 w-8 text-primary" />
                              </div>
                            }
                            streakData={pairingStreaks}
                            levelData={pairingLevels}
                          />
                          {inactiveHistory.length === 0 && (
                            <motion.div
                              initial={{ opacity: 0, y: 20 }}
                              animate={{ opacity: 1, y: 0 }}
                              className="text-center"
                            >
                              <p className="text-theme-secondary text-sm">
                                No ended matches to display in the history.
                              </p>
                            </motion.div>
                          )}
                        </CardContent>
                      </Card>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.div>
            </div>
          </div>
        </div>

        {/* Modals */}
        <AnimatePresence>
          {modalState.showUserProfileModal && modalState.selectedUserProfile && (
            <UserProfileModal
              key="user-profile-modal"
              isOpen={modalState.showUserProfileModal}
              onClose={handleCloseUserProfileModal}
              userProfile={modalState.selectedUserProfile}
              position={modalState.userProfileModalPosition}
            />
          )}

          {isAllMatchesModalOpen && (
            <AllMatchesModal
              key="all-matches-modal"
              isOpen={isAllMatchesModalOpen}
              onClose={() => setIsAllMatchesModalOpen(false)}
              pairings={currentMatches}
              userProfiles={userProfiles}
              onUserClick={handleUserClick}
            />
          )}

          {modalState.showMatchModal && modalState.matchedPairing && (
            <MatchFoundModal
              key="match-found-modal"
              pairing={modalState.matchedPairing}
              onClose={handleCloseMatchModal}
            />
          )}

          {modalState.showNoMatchModal && modalState.noMatchData && (
            <NoMatchFoundModal
              key="no-match-modal"
              onClose={handleCloseNoMatchModal}
              onStayInQueue={handleStayInQueue}
              onLeaveQueue={handleLeaveQueueFromModal}
              totalInQueue={modalState.noMatchData.totalInQueue}
              message={modalState.noMatchData.message}
            />
          )}

          {modalState.showQueueRemovedModal && modalState.queueRemovedMessage && (
            <QueueRemovedModal
              key="queue-removed-modal"
              message={modalState.queueRemovedMessage}
              onClose={handleCloseQueueRemovedModal}
            />
          )}

          {modalState.showBreakupModal && currentPairing && (
            <BreakupModal
              key="breakup-modal"
              isOpen={modalState.showBreakupModal}
              onClose={() => hideBreakup()}
              onConfirm={handleBreakup}
              partnerName={pairedUser?.displayName || "your match"}
            />
          )}

          {modalState.showBreakupSuccessModal && (
            <BreakupSuccessModal
              key="breakup-success-modal"
              isOpen={modalState.showBreakupSuccessModal}
              onClose={handleCloseBreakupSuccessModal}
              partnerName={pairedUser?.displayName || "your match"}
            />
          )}

          {modalState.showPartnerUnmatchedModal && (
            <PartnerUnmatchedModal
              key="partner-unmatched-modal"
              isOpen={modalState.showPartnerUnmatchedModal}
              onClose={handleClosePartnerUnmatchedModal}
              onJoinQueue={handleJoinQueueFromPartnerModal}
              partnerName={modalState.offlineBreakupPartnerName || pairedUser?.displayName || "your match"}
            />
          )}

          {modalState.showQueueStatsModal && hasRole("ADMIN") && (
            <QueueStatsModal
              key="queue-stats-modal"
              isOpen={modalState.showQueueStatsModal}
              onClose={hideQueueStats}
            />
          )}

          {showAdminPairModal && selectedPairingForAdmin && hasRole("ADMIN") && (
            <AdminPairManagementModal
              key="admin-pair-management-modal"
              isOpen={showAdminPairModal}
              onClose={handleCloseAdminPairModal}
              pairing={selectedPairingForAdmin}
              userProfiles={userProfiles}
              onPairingUpdated={refreshData}
            />
          )}
        </AnimatePresence>
    </ErrorBoundary>
  )
}

// Queue Removed Modal Component
const QueueRemovedModal = ({ message, onClose }: { message: string | null; onClose: () => void }) => (
  <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
    <motion.div
      initial={{ scale: 0.8, opacity: 0, y: 30 }}
      animate={{ scale: 1, opacity: 1, y: 0 }}
      exit={{ scale: 0.8, opacity: 0, y: 30 }}
      className="relative w-full max-w-md"
    >
      <Card className="valorant-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-3 text-status-warning">
            <AlertCircle className="h-6 w-6" />
            Queue Disabled
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-theme-secondary">{message}</p>
          <Button onClick={onClose} className="w-full valorant-button-primary">
            <Heart className="mr-2 h-5 w-5" />
            Understood
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  </div>
)
