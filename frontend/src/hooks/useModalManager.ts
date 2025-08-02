import { useState, useCallback, useMemo } from 'react'
import type { PairingDTO } from '@/config/pairingService'
import type { UserProfileDTO } from '@/config/userService'

// Modal state interface with better type safety
interface ModalState {
  showUserProfileModal: boolean
  selectedUserProfile: UserProfileDTO | null
  userProfileModalPosition: { x: number; y: number } | null
  showBreakupModal: boolean
  showBreakupSuccessModal: boolean
  showPartnerUnmatchedModal: boolean
  offlineBreakupPartnerName: string | null
}

// Initial modal state
const initialModalState: ModalState = {
  showUserProfileModal: false,
  selectedUserProfile: null,
  userProfileModalPosition: null,
  showBreakupModal: false,
  showBreakupSuccessModal: false,
  showPartnerUnmatchedModal: false,
  offlineBreakupPartnerName: null,
}

export const useModalManager = () => {
  const [modalState, setModalState] = useState<ModalState>(initialModalState)

  // Optimized modal state update function
  const updateModal = useCallback((updates: Partial<ModalState>) => {
    setModalState(prev => ({ ...prev, ...updates }))
  }, [])

  // Individual modal control functions with memoization
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

  // Reset all modals
  const resetModals = useCallback(() => {
    setModalState(initialModalState)
  }, [])

  return useMemo(() => ({
    // State
    modalState,
    
    // Actions
    showUserProfile,
    hideUserProfile,
    showBreakup,
    hideBreakup,
    showBreakupSuccess,
    hideBreakupSuccess,
    showPartnerUnmatched,
    hidePartnerUnmatched,
    resetModals,
    updateModal,
  }), [
    modalState,
    showUserProfile,
    hideUserProfile,
    showBreakup,
    hideBreakup,
    showBreakupSuccess,
    hideBreakupSuccess,
    showPartnerUnmatched,
    hidePartnerUnmatched,
    resetModals,
    updateModal,
  ])
}

export type ModalManager = ReturnType<typeof useModalManager> 