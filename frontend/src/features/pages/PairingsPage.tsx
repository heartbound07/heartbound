"use client"

import type React from "react"

import { useState, useEffect, useCallback, useMemo } from "react"
import { useAuth } from "@/contexts/auth/useAuth"
import { usePairings } from "@/hooks/usePairings"
import { useAllActivePairings } from "@/hooks/useAllActivePairings"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Heart, Users, AlertCircle, UserCheck, Activity, Trash2, MessageCircle } from 'lucide-react'
import { motion, AnimatePresence } from "framer-motion"
import type { PairingDTO } from "@/config/pairingService"
import { deleteAllPairings } from "@/config/pairingService"
import { usePairingUpdates } from "@/hooks/usePairingUpdates"
import { UserProfileModal } from "@/components/modals/UserProfileModal"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { useXPData, invalidateXPData } from "@/hooks/useXPData"
import { useModalManager } from "@/hooks/useModalManager"

import "@/assets/PairingsPage.css"
import { Skeleton } from "@/components/ui/SkeletonUI"
import { BreakupModal } from "@/components/modals/BreakupModal"
import { BreakupSuccessModal } from "@/components/modals/BreakupSuccessModal"
import { PartnerUnmatchedModal } from "@/components/modals/PartnerUnmatchedModal"
import { AdminPairManagementModal } from "@/components/modals/AdminPairManagementModal"

import { PairingCardList } from "./PairingCard"
import { ErrorBoundary } from "@/components/ui/ErrorBoundary"
import { XPCard } from "@/features/pages/XPCard"
import { AllMatchesModal } from "@/components/modals/AllMatchesModal"
import { useAllPairingHistory } from "@/hooks/useAllPairingHistory"

// Import extracted components
import { MatchedPairing } from "@/features/pages/pairings/components/MatchedPairing"

// Admin state interface
interface AdminState {
  actionLoading: boolean
  message: string | null
}

// Initial admin state
const initialAdminState: AdminState = {
  actionLoading: false,
  message: null,
}

// Simple date formatter function
const formatDate = (dateString: string): string => {
  try {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch (error) {
    return 'Invalid date'
  }
}

export function PairingsPage() {
  const { user, hasRole } = useAuth()
  const {
    currentPairing,
    pairingHistory,
    pairedUser,
    loading,
    error,
    actionLoading,
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
  
  const { pairingUpdate, clearUpdate } = usePairingUpdates()

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
  const pairingIds = useMemo(() => {
    const ids: number[] = []
    if (currentPairing) ids.push(currentPairing.id)
    if (allActivePairings) {
      allActivePairings.forEach(pairing => ids.push(pairing.id))
    }
    return ids
  }, [currentPairing, allActivePairings])
  
  const { 
    levelData: pairingLevels, 
    streakData: pairingStreaks
  } = useXPData(pairingIds)
  
  // Admin Pair Management Modal state
  const [showAdminPairModal, setShowAdminPairModal] = useState(false)
  const [selectedPairingForAdmin, setSelectedPairingForAdmin] = useState<PairingDTO | null>(null)

  // Minimum loading time in milliseconds
  const MIN_LOADING_TIME = 800

  // Track the last known pairing to detect offline breakups
  const LAST_PAIRING_KEY = useMemo(() => `last-pairing-${user?.id}`, [user?.id])

  // Extracted modal management functions from useModalManager
  const {
    modalState,
    showBreakup,
    hideBreakup,
    showBreakupSuccess,
    hideBreakupSuccess,
    showPartnerUnmatched,
    hidePartnerUnmatched,
    showUserProfile,
    hideUserProfile
  } = modalManager

  // Helper function to update admin state
  const updateAdminState = useCallback((updates: Partial<AdminState>) => {
    setAdminState(prev => ({ ...prev, ...updates }))
  }, [])

  // Optimized effect for minimum loading time
  useEffect(() => {
    const timer = setTimeout(() => {
      setIsInitialLoading(false)
    }, MIN_LOADING_TIME)

    return () => clearTimeout(timer)
  }, [])

  // Handle pairing updates from WebSocket
  useEffect(() => {
    if (pairingUpdate) {
      console.log('[PairingsPage] Received pairing update:', pairingUpdate)
      
      if (pairingUpdate.eventType === 'MATCH_FOUND') {
        console.log('[PairingsPage] Match found, refreshing data')
        refreshData()
        invalidateXPData(pairingIds)
      } else if (pairingUpdate.eventType === 'PAIRING_ENDED') {
        console.log('[PairingsPage] Partner breakup detected')
        refreshData()
        if (!userInitiatedBreakup) {
          showPartnerUnmatched()
        }
        setUserInitiatedBreakup(false)
        invalidateXPData(pairingIds)
      }
      
      clearUpdate()
    }
  }, [pairingUpdate, clearUpdate, refreshData, showPartnerUnmatched, userInitiatedBreakup, currentPairing, pairingIds])

  // Detect offline breakups by comparing current pairing with stored pairing
  useEffect(() => {
    if (!user?.id) return

    const storedPairingId = localStorage.getItem(LAST_PAIRING_KEY)
    
    if (storedPairingId && !currentPairing) {
      // User had a pairing before but doesn't now - likely an offline breakup
      console.log('[PairingsPage] Detected offline breakup')
      if (!userInitiatedBreakup) {
        showPartnerUnmatched()
      }
      localStorage.removeItem(LAST_PAIRING_KEY)
      setUserInitiatedBreakup(false)
    } else if (currentPairing) {
      // Store current pairing ID
      localStorage.setItem(LAST_PAIRING_KEY, currentPairing.id.toString())
    }
  }, [currentPairing, user?.id, LAST_PAIRING_KEY, showPartnerUnmatched, userInitiatedBreakup])

  // Fetch user profiles for display
  useEffect(() => {
    const fetchUserProfiles = async () => {
      const userIds = new Set<string>()
      
      // Add users from current pairing
      if (currentPairing) {
        userIds.add(currentPairing.user1Id)
        userIds.add(currentPairing.user2Id)
      }
      
      // Add users from pairing history
      pairingHistory.forEach(pairing => {
        userIds.add(pairing.user1Id)
        userIds.add(pairing.user2Id)
      })
      
      // Add users from all active pairings (for admin)
      if (hasRole("ADMIN")) {
        allActivePairings?.forEach(pairing => {
          userIds.add(pairing.user1Id)
          userIds.add(pairing.user2Id)
        })
      }
      
      if (userIds.size > 0) {
        try {
          const profiles = await getUserProfiles(Array.from(userIds))
          setUserProfiles(profiles)
        } catch (error) {
          console.error('Error fetching user profiles:', error)
        }
      }
    }

    fetchUserProfiles()
  }, [currentPairing, pairingHistory, allActivePairings, hasRole])

  const handleUserClick = useCallback(
    (userId: string) => {
      const profile = userProfiles[userId]
      if (profile) {
        showUserProfile(profile, { x: 0, y: 0 })
      }
    },
    [userProfiles, showUserProfile],
  )

  const handleCloseUserProfileModal = useCallback(() => {
    hideUserProfile()
  }, [hideUserProfile])

  const handleUnpairUsers = async (pairingId: number, event: React.MouseEvent) => {
    event.stopPropagation()
    
    if (!confirm('Are you sure you want to unpair these users? This will end their active pairing but keep the blacklist entry.')) {
      return
    }
    
    try {
      await unpairPairing(pairingId)
      refreshData()
      if (hasRole("ADMIN") && refreshAllPairingHistory) {
        refreshAllPairingHistory()
      }
    } catch (error) {
      console.error('Error unpairing users:', error)
    }
  }

  const handleDeletePairing = async (pairingId: number, event: React.MouseEvent) => {
    event.stopPropagation()
    
    if (!confirm('Are you sure you want to permanently delete this pairing record? This action cannot be undone.')) {
      return
    }
    
    try {
      await deletePairing(pairingId)
      refreshData()
      if (hasRole("ADMIN") && refreshAllPairingHistory) {
        refreshAllPairingHistory()
      }
    } catch (error) {
      console.error('Error deleting pairing:', error)
    }
  }

  const handleClearInactiveHistory = async () => {
    if (!confirm('Are you sure you want to permanently delete ALL inactive pairing records? This action cannot be undone.')) {
      return
    }
    
    try {
      await clearInactiveHistory()
      refreshData()
      if (hasRole("ADMIN") && refreshAllPairingHistory) {
        refreshAllPairingHistory()
      }
    } catch (error) {
      console.error('Error clearing inactive history:', error)
    }
  }

  // Delete all active pairings (admin only)
  const handleDeleteAllPairings = async () => {
    if (!confirm("Are you sure you want to delete ALL active pairings? This action cannot be undone.")) {
      return
    }
    
    try {
      updateAdminState({ actionLoading: true, message: null })
      
      const result = await deleteAllPairings()
      updateAdminState({ 
        message: `${result.message} (${result.deletedCount} pairings deleted)` 
      })
      
      refreshData()
      if (hasRole("ADMIN") && refreshAllPairingHistory) {
        refreshAllPairingHistory()
      }
      
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || "Failed to delete pairings"
      updateAdminState({ message: `Error: ${errorMessage}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } finally {
      updateAdminState({ actionLoading: false })
    }
  }

  const handleBreakup = useCallback(async (reason: string) => {
    if (!currentPairing) return
    
    setUserInitiatedBreakup(true)
    await breakupPairing(currentPairing.id, reason)
    hideBreakup()
    showBreakupSuccess()
    invalidateXPData(pairingIds)
  }, [currentPairing, breakupPairing, hideBreakup, showBreakupSuccess, pairingIds])

  const handleCloseBreakupModal = useCallback(() => {
    hideBreakup()
  }, [hideBreakup])

  const handleCloseBreakupSuccessModal = useCallback(() => {
    hideBreakupSuccess()
  }, [hideBreakupSuccess])

  const handleClosePartnerUnmatchedModal = useCallback(() => {
    hidePartnerUnmatched()
  }, [hidePartnerUnmatched])

  const handleInitiateBreakup = useCallback(() => {
    showBreakup()
  }, [showBreakup])

  const handleShowAllMatches = () => {
    setIsAllMatchesModalOpen(true)
  }

  const handleCloseAllMatchesModal = () => {
    setIsAllMatchesModalOpen(false)
  }

  const handleAdminPairManagement = (pairing: PairingDTO) => {
    setSelectedPairingForAdmin(pairing)
    setShowAdminPairModal(true)
  }

  const handleCloseAdminPairModal = () => {
    setShowAdminPairModal(false)
    setSelectedPairingForAdmin(null)
  }

  const handleSaveAdminPairChanges = () => {
    refreshData()
    if (hasRole("ADMIN") && refreshAllPairingHistory) {
      refreshAllPairingHistory()
    }
    invalidateXPData(pairingIds)
  }

  // Show loading skeleton while data is being fetched
  if (loading || isInitialLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-4">
        <div className="mx-auto max-w-4xl space-y-6">
          <Skeleton className="h-12 w-64" />
          <div className="grid gap-6 md:grid-cols-2">
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
          </div>
          <Skeleton className="h-96" />
        </div>
      </div>
    )
  }

  return (
    <ErrorBoundary>
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900 p-4">
        <div className="mx-auto max-w-4xl space-y-6">
          {/* Header */}
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center"
          >
            <h1 className="text-4xl font-bold bg-gradient-to-r from-pink-400 to-purple-600 bg-clip-text text-transparent">
              Pairing Dashboard
            </h1>
            <p className="text-slate-400 mt-2">
              {currentPairing 
                ? "You're currently paired! Check your progress below." 
                : "You are not currently in a match."}
            </p>
          </motion.div>

          {/* Error Display */}
          {error && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="rounded-lg bg-red-500/10 border border-red-500/20 p-4"
            >
              <div className="flex items-center space-x-2">
                <AlertCircle className="h-5 w-5 text-red-400" />
                <span className="text-red-400">{error}</span>
              </div>
            </motion.div>
          )}

          {/* Main Content Grid */}
          <div className="grid gap-6 md:grid-cols-2">
            {/* Current Pairing Section */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.1 }}
            >
              <Card className="bg-slate-800/50 border-slate-700 backdrop-blur-sm">
                <CardHeader>
                  <CardTitle className="text-white flex items-center space-x-2">
                    <Heart className="h-5 w-5 text-pink-400" />
                    <span>Current Match</span>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {currentPairing && pairedUser ? (
                    <MatchedPairing
                      currentPairing={currentPairing}
                      pairedUser={pairedUser}
                      user={user}
                      onBreakup={handleInitiateBreakup}
                      onUserClick={handleUserClick}
                      actionLoading={actionLoading}
                      formatDate={formatDate}
                    />
                  ) : (
                    <div className="text-center py-8 text-slate-400">
                      <Heart className="h-12 w-12 mx-auto mb-4 text-slate-600" />
                      <p className="text-lg font-medium">No Active Match</p>
                      <p className="text-sm">Matchmaking is currently disabled</p>
                    </div>
                  )}
                </CardContent>
              </Card>
            </motion.div>

            {/* XP Progress Card */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.2 }}
            >
              {currentPairing ? (
                <XPCard 
                  pairingId={currentPairing.id}
                />
              ) : (
                <Card className="bg-slate-800/50 border-slate-700 backdrop-blur-sm">
                  <CardHeader>
                    <CardTitle className="text-white flex items-center space-x-2">
                      <Activity className="h-5 w-5 text-blue-400" />
                      <span>Progress Tracking</span>
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="text-center py-8 text-slate-400">
                      <Activity className="h-12 w-12 mx-auto mb-4 text-slate-600" />
                      <p className="text-lg font-medium">No Progress to Show</p>
                      <p className="text-sm">Get matched to start tracking your progress!</p>
                    </div>
                  </CardContent>
                </Card>
              )}
            </motion.div>
          </div>

          {/* Admin Controls */}
          {hasRole("ADMIN") && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
            >
              <Card className="bg-slate-800/50 border-slate-700 backdrop-blur-sm">
                <CardHeader>
                  <CardTitle className="text-white flex items-center space-x-2">
                    <UserCheck className="h-5 w-5 text-green-400" />
                    <span>Admin Controls</span>
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {adminState.message && (
                      <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/20">
                        <p className="text-blue-400 text-sm">{adminState.message}</p>
                      </div>
                    )}
                    
                    <div className="flex flex-wrap gap-3">
                      <Button
                        onClick={handleShowAllMatches}
                        className="bg-blue-600 hover:bg-blue-700"
                        disabled={allActivePairingsLoading}
                      >
                        <Users className="h-4 w-4 mr-2" />
                        View All Matches ({allActivePairings?.length || 0})
                      </Button>
                      
                      <Button
                        onClick={handleDeleteAllPairings}
                        variant="destructive"
                        disabled={adminState.actionLoading}
                      >
                        <Trash2 className="h-4 w-4 mr-2" />
                        Delete All Pairings
                      </Button>
                      
                      <Button
                        onClick={handleClearInactiveHistory}
                        variant="outline"
                        className="border-slate-600 text-slate-300 hover:bg-slate-700"
                      >
                        <Trash2 className="h-4 w-4 mr-2" />
                        Clear History
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          )}

          {/* Pairing History */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
          >
            <Card className="bg-slate-800/50 border-slate-700 backdrop-blur-sm">
              <CardHeader>
                <CardTitle className="text-white flex items-center space-x-2">
                  <MessageCircle className="h-5 w-5 text-purple-400" />
                  <span>Pairing History</span>
                  {pairingHistory.length > 0 && (
                    <Badge variant="secondary" className="ml-2">
                      {pairingHistory.length}
                    </Badge>
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent>
                {pairingHistory.length > 0 ? (
                  <PairingCardList
                    pairings={pairingHistory}
                    userProfiles={userProfiles}
                    isActive={false}
                    onUserClick={handleUserClick}
                    onUnpair={hasRole("ADMIN") ? handleUnpairUsers : undefined}
                    onDelete={hasRole("ADMIN") ? handleDeletePairing : undefined}
                    onManagePair={hasRole("ADMIN") ? handleAdminPairManagement : undefined}
                    formatDate={formatDate}
                    hasAdminActions={hasRole("ADMIN")}
                    streakData={pairingStreaks}
                    levelData={pairingLevels}
                  />
                ) : (
                  <div className="text-center py-8 text-slate-400">
                    <MessageCircle className="h-12 w-12 mx-auto mb-4 text-slate-600" />
                    <p className="text-lg font-medium">No Pairing History</p>
                    <p className="text-sm">Your past matches will appear here</p>
                  </div>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </div>

        {/* Modals */}
        <AnimatePresence>
          {modalState.showBreakupModal && currentPairing && (
            <BreakupModal
              isOpen={modalState.showBreakupModal}
              onClose={handleCloseBreakupModal}
              onConfirm={handleBreakup}
              partnerName={pairedUser?.displayName}
            />
          )}

          {modalState.showBreakupSuccessModal && (
            <BreakupSuccessModal
              isOpen={modalState.showBreakupSuccessModal}
              onClose={handleCloseBreakupSuccessModal}
            />
          )}

          {modalState.showPartnerUnmatchedModal && (
            <PartnerUnmatchedModal
              isOpen={modalState.showPartnerUnmatchedModal}
              onClose={handleClosePartnerUnmatchedModal}
            />
          )}

          {modalState.showUserProfileModal && modalState.selectedUserProfile && (
            <UserProfileModal
              isOpen={modalState.showUserProfileModal}
              onClose={handleCloseUserProfileModal}
              userProfile={modalState.selectedUserProfile}
            />
          )}

          {isAllMatchesModalOpen && (
            <AllMatchesModal
              isOpen={isAllMatchesModalOpen}
              onClose={handleCloseAllMatchesModal}
              pairings={allActivePairings || []}
              userProfiles={userProfiles}
              onUserClick={handleUserClick}
            />
          )}

          {showAdminPairModal && selectedPairingForAdmin && (
            <AdminPairManagementModal
              isOpen={showAdminPairModal}
              onClose={handleCloseAdminPairModal}
              pairing={selectedPairingForAdmin}
              userProfiles={userProfiles}
              onPairingUpdated={handleSaveAdminPairChanges}
            />
          )}
        </AnimatePresence>
      </div>
    </ErrorBoundary>
  )
}
