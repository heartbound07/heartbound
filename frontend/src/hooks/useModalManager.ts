import { useState, useCallback, useMemo } from 'react'
import type { PairingDTO } from '@/config/pairingService'
import type { UserProfileDTO } from '@/config/userService'

// Modal state interface with better type safety
interface ModalState {
  showMatchModal: boolean
  matchedPairing: PairingDTO | null
  showUserProfileModal: boolean
  selectedUserProfile: UserProfileDTO | null
  userProfileModalPosition: { x: number; y: number } | null
  showNoMatchModal: boolean
  noMatchData: { totalInQueue?: number; message?: string } | null
  showQueueRemovedModal: boolean
  queueRemovedMessage: string | null
  showBreakupModal: boolean
  showBreakupSuccessModal: boolean
  showPartnerUnmatchedModal: boolean
  offlineBreakupPartnerName: string | null
  showQueueStatsModal: boolean
}

// Initial modal state
const initialModalState: ModalState = {
  showMatchModal: false,
  matchedPairing: null,
  showUserProfileModal: false,
  selectedUserProfile: null,
  userProfileModalPosition: null,
  showNoMatchModal: false,
  noMatchData: null,
  showQueueRemovedModal: false,
  queueRemovedMessage: null,
  showBreakupModal: false,
  showBreakupSuccessModal: false,
  showPartnerUnmatchedModal: false,
  offlineBreakupPartnerName: null,
  showQueueStatsModal: false,
}

export const useModalManager = () => {
  const [modalState, setModalState] = useState<ModalState>(initialModalState)

  // Optimized modal state update function
  const updateModal = useCallback((updates: Partial<ModalState>) => {
    setModalState(prev => ({ ...prev, ...updates }))
  }, [])

  // Individual modal control functions with memoization
  const showMatchFound = useCallback((pairing: PairingDTO) => {
    updateModal({ showMatchModal: true, matchedPairing: pairing })
  }, [updateModal])

  const hideMatchFound = useCallback(() => {
    updateModal({ showMatchModal: false, matchedPairing: null })
  }, [updateModal])

  const showUserProfile = useCallback((profile: UserProfileDTO, position: { x: number; y: number }) => {
    updateModal({ 
      showUserProfileModal: true, 
      selectedUserProfile: profile, 
      userProfileModalPosition: position 
    })
  }, [updateModal])

  const hideUserProfile = useCallback(() => {
    updateModal({ 
      showUserProfileModal: false, 
      selectedUserProfile: null, 
      userProfileModalPosition: null 
    })
  }, [updateModal])

  const showNoMatch = useCallback((data: { totalInQueue?: number; message?: string }) => {
    updateModal({ showNoMatchModal: true, noMatchData: data })
  }, [updateModal])

  const hideNoMatch = useCallback(() => {
    updateModal({ showNoMatchModal: false, noMatchData: null })
  }, [updateModal])

  const showQueueRemoved = useCallback((message: string) => {
    updateModal({ showQueueRemovedModal: true, queueRemovedMessage: message })
  }, [updateModal])

  const hideQueueRemoved = useCallback(() => {
    updateModal({ showQueueRemovedModal: false, queueRemovedMessage: null })
  }, [updateModal])

  const showBreakup = useCallback(() => {
    updateModal({ showBreakupModal: true })
  }, [updateModal])

  const hideBreakup = useCallback(() => {
    updateModal({ showBreakupModal: false })
  }, [updateModal])

  const showBreakupSuccess = useCallback(() => {
    updateModal({ showBreakupSuccessModal: true })
  }, [updateModal])

  const hideBreakupSuccess = useCallback(() => {
    updateModal({ showBreakupSuccessModal: false })
  }, [updateModal])

  const showPartnerUnmatched = useCallback((partnerName?: string | null) => {
    updateModal({ 
      showPartnerUnmatchedModal: true, 
      offlineBreakupPartnerName: partnerName || null 
    })
  }, [updateModal])

  const hidePartnerUnmatched = useCallback(() => {
    updateModal({ 
      showPartnerUnmatchedModal: false, 
      offlineBreakupPartnerName: null 
    })
  }, [updateModal])

  const showQueueStats = useCallback(() => {
    updateModal({ showQueueStatsModal: true })
  }, [updateModal])

  const hideQueueStats = useCallback(() => {
    updateModal({ showQueueStatsModal: false })
  }, [updateModal])

  // Reset all modals
  const resetModals = useCallback(() => {
    setModalState(initialModalState)
  }, [])

  return useMemo(() => ({
    // State
    modalState,
    
    // Actions
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
    resetModals,
    updateModal,
  }), [
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
    resetModals,
    updateModal,
  ])
}

export type ModalManager = ReturnType<typeof useModalManager> 