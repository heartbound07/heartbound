"use client"

import type React from "react"

import { useState, useEffect, useCallback, useMemo, useRef, memo } from "react"
import { useAuth } from "@/contexts/auth/useAuth"
import { usePairings } from "@/hooks/usePairings"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/valorant/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Heart, Users, Trophy, MessageCircle, MessageSquare, Settings, User, MapPin, Calendar, AlertCircle, Clock, Zap, UserCheck, Activity, Trash2} from 'lucide-react'
import { motion, AnimatePresence } from "framer-motion"
import type { JoinQueueRequestDTO } from "@/config/pairingService"
import { useQueueUpdates } from "@/contexts/QueueUpdates"
import { performMatchmaking, deleteAllPairings, enableQueue, disableQueue } from "@/config/pairingService"
import { usePairingUpdates } from "@/contexts/PairingUpdates"
import { MatchFoundModal } from "@/components/modals/MatchFoundModal"
import { UserProfileModal } from "@/components/modals/UserProfileModal"
import { getUserProfiles, type UserProfileDTO } from "@/config/userService"
import { DashboardNavigation } from "@/components/Sidebar"
import "@/assets/PairingsPage.css"
import { useQueueConfig } from "@/contexts/QueueConfigUpdates"
import { Skeleton } from "@/components/ui/SkeletonUI"
import { NoMatchFoundModal } from "@/components/modals/NoMatchFoundModal"
import { BreakupModal } from "@/components/modals/BreakupModal"
import { BreakupSuccessModal } from "@/components/modals/BreakupSuccessModal"
import { PartnerUnmatchedModal } from "@/components/modals/PartnerUnmatchedModal"
import { useModalManager } from "@/hooks/useModalManager"
import { PairingCardList } from "./PairingCard"
import { ErrorBoundary } from "@/components/ui/ErrorBoundary"
import { XPCard } from "@/features/pages/XPCard"

// Constants moved to top level for better performance
const REGIONS = [
  { value: "NA_EAST", label: "NA East" },
  { value: "NA_WEST", label: "NA West" },
  { value: "NA_CENTRAL", label: "NA Central" },
  { value: "EU", label: "Europe" },
  { value: "AP", label: "Asia Pacific" },
  { value: "KR", label: "Korea" },
  { value: "LATAM", label: "Latin America" },
  { value: "BR", label: "Brazil" },
] as const

const RANKS = [
  { value: "IRON", label: "Iron" },
  { value: "BRONZE", label: "Bronze" },
  { value: "SILVER", label: "Silver" },
  { value: "GOLD", label: "Gold" },
  { value: "PLATINUM", label: "Platinum" },
  { value: "DIAMOND", label: "Diamond" },
  { value: "ASCENDANT", label: "Ascendant" },
  { value: "IMMORTAL", label: "Immortal" },
  { value: "RADIANT", label: "Radiant" },
] as const

const GENDERS = [
  { value: "MALE", label: "Male" },
  { value: "FEMALE", label: "Female" },
  { value: "NON_BINARY", label: "Non-Binary" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" },
] as const

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

// Enhanced Queue Join Form with better UX - Memoized for performance
const QueueJoinForm = memo(({
  onJoinQueue,
  loading,
}: {
  onJoinQueue: (data: JoinQueueRequestDTO) => Promise<void>
  loading: boolean
}) => {
  const [formData, setFormData] = useState({
    age: "",
    region: "",
    rank: "",
    gender: ""
  })

  // Secure input validation
  const validateAge = useCallback((age: string): boolean => {
    const ageNum = Number.parseInt(age)
    return !Number.isNaN(ageNum) && ageNum >= 13 && ageNum <= 100
  }, [])

  // Sanitize input to prevent XSS
  const sanitizeInput = useCallback((input: string): string => {
    return input.trim().replace(/[<>'"]/g, '')
  }, [])

  const updateFormField = useCallback((field: keyof typeof formData, value: string) => {
    setFormData(prev => ({
      ...prev,
      [field]: field === 'age' ? value : sanitizeInput(value)
    }))
  }, [sanitizeInput])

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()

      const ageNum = Number.parseInt(formData.age)
      if (!validateAge(formData.age)) {
        throw new Error("Please enter a valid age between 13 and 100")
      }

      if (!formData.region || !formData.rank || !formData.gender) {
        throw new Error("Please fill in all fields")
      }

      await onJoinQueue({
        userId: "",
        age: ageNum,
        region: formData.region as any,
        rank: formData.rank as any,
        gender: formData.gender as any,
      })

      // Reset form on successful submission
      setFormData({ age: "", region: "", rank: "", gender: "" })
    },
    [formData, onJoinQueue, validateAge],
  )

  const isFormValid = useMemo(() => 
    validateAge(formData.age) && formData.region && formData.rank && formData.gender,
    [formData, validateAge]
  )

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
      <Card className="valorant-card">
        <CardHeader className="pb-4">
          <CardTitle className="flex items-center gap-3 text-white text-xl">
            <div className="p-2 bg-primary/20 rounded-lg">
              <Heart className="h-5 w-5 text-primary" />
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label htmlFor="age" className="text-sm font-medium text-[var(--color-text-primary)]">
                  Age
                </Label>
                <Input
                  id="age"
                  type="number"
                  placeholder="Enter your age"
                  value={formData.age}
                  onChange={(e) => updateFormField('age', e.target.value)}
                  className="bg-[var(--color-container-bg)] border-[var(--color-border)] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-primary focus:ring-1 focus:ring-primary/20"
                  min="13"
                  max="100"
                  required
                  aria-describedby="age-error"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="gender" className="text-sm font-medium text-[var(--color-text-primary)]">
                  Gender
                </Label>
                <Select value={formData.gender} onValueChange={(value) => updateFormField('gender', value)} required>
                  <SelectTrigger className="bg-[#1F2731] border-[#374151] text-white">
                    <SelectValue placeholder="Select gender" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-[#374151]">
                    {GENDERS.map((g) => (
                      <SelectItem 
                        key={g.value} 
                        value={g.value}
                        className="text-white hover:bg-[#2D3748] focus:bg-[#2D3748]"
                      >
                        {g.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.3 }}>
                <Label htmlFor="region" className="text-slate-200 font-medium mb-2 block">
                  Region
                </Label>
                <Select value={formData.region} onValueChange={(value) => updateFormField('region', value)} required>
                  <SelectTrigger className="bg-[#1F2731] border-[#374151] text-white">
                    <SelectValue placeholder="Select region" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-[#374151]">
                    {REGIONS.map((reg) => (
                      <SelectItem 
                        key={reg.value} 
                        value={reg.value}
                        className="text-white hover:bg-[#2D3748] focus:bg-[#2D3748]"
                      >
                        {reg.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </motion.div>

              <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.4 }}>
                <Label htmlFor="rank" className="text-slate-200 font-medium mb-2 block">
                  VALORANT Rank
                </Label>
                <Select value={formData.rank} onValueChange={(value) => updateFormField('rank', value)} required>
                  <SelectTrigger className="bg-[#1F2731] border-[#374151] text-white">
                    <SelectValue placeholder="Select rank" />
                  </SelectTrigger>
                  <SelectContent className="bg-[#1F2731] border-[#374151]">
                    {RANKS.map((r) => (
                      <SelectItem 
                        key={r.value} 
                        value={r.value}
                        className="text-white hover:bg-[#2D3748] focus:bg-[#2D3748]"
                      >
                        {r.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </motion.div>
            </div>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}>
              <Button
                type="submit"
                className="w-full valorant-button-primary h-12 text-base font-semibold"
                disabled={loading || !isFormValid}
              >
                {loading ? (
                  <Skeleton width="120px" height="20px" theme="valorant" className="mx-auto" />
                ) : (
                  <>
                    <Heart className="mr-2 h-5 w-5" />
                    Join the Queue
                  </>
                )}
              </Button>
            </motion.div>
          </form>
        </CardContent>
      </Card>
    </motion.div>
  )
})

QueueJoinForm.displayName = 'QueueJoinForm'

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
  const { isConnected } = useQueueUpdates()
  const { pairingUpdate, clearUpdate } = usePairingUpdates()

  // Use optimized modal manager
  const modalManager = useModalManager()
  const [adminState, setAdminState] = useState<AdminState>(initialAdminState)
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({})
  const [userInitiatedBreakup, setUserInitiatedBreakup] = useState(false)

  // Sidebar state
  const [isCollapsed, setIsCollapsed] = useState(() => {
    const savedState = localStorage.getItem("sidebar-collapsed")
    return savedState ? JSON.parse(savedState) : false
  })

  // Queue timer state with ref for performance
  const [queueTimer, setQueueTimer] = useState<string>('0s')
  const queueStartTimeRef = useRef<number | null>(null)
  const timerIntervalRef = useRef<NodeJS.Timeout | null>(null)

  // Track the last known pairing to detect offline breakups
  const LAST_PAIRING_KEY = useMemo(() => `last-pairing-${user?.id}`, [user?.id])

  // Optimized queue timer effect with cleanup
  useEffect(() => {
    if (queueStatus.inQueue) {
      queueStartTimeRef.current = Date.now()
      setQueueTimer('0s')
      
      timerIntervalRef.current = setInterval(() => {
        if (queueStartTimeRef.current) {
          const elapsed = Math.floor((Date.now() - queueStartTimeRef.current) / 1000)
          
          const hours = Math.floor(elapsed / 3600)
          const minutes = Math.floor((elapsed % 3600) / 60)
          const seconds = elapsed % 60

          if (hours > 0) {
            setQueueTimer(`${hours}h ${minutes}m`)
          } else if (minutes > 0) {
            setQueueTimer(`${minutes}m ${seconds}s`)
          } else {
            setQueueTimer(`${seconds}s`)
          }
        }
      }, 1000)
    } else {
      setQueueTimer('0s')
      queueStartTimeRef.current = null
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
  }, [queueStatus.inQueue])

  // Optimized sidebar state listener
  useEffect(() => {
    const handleSidebarStateChange = (event: CustomEvent) => {
      setIsCollapsed(event.detail.collapsed)
    }

    window.addEventListener("sidebarStateChange", handleSidebarStateChange as EventListener)
    return () => {
      window.removeEventListener("sidebarStateChange", handleSidebarStateChange as EventListener)
    }
  }, [])

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
        age: Math.max(13, Math.min(100, queueData.age)), // Clamp age values
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
        clearUpdate()
      }
    }
  }, [pairingUpdate, refreshData, clearUpdate, showMatchFound, showNoMatch, showQueueRemoved, showBreakupSuccess, showPartnerUnmatched])

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

      pairingHistory.forEach((pairing) => {
        userIds.add(pairing.user1Id)
        userIds.add(pairing.user2Id)
      })

      if (userIds.size > 0) {
        try {
          const profiles = await getUserProfiles(Array.from(userIds))
          setUserProfiles(profiles)
        } catch (error) {
          console.error("Failed to fetch user profiles:", error)
        }
      }
    }

    if (pairingHistory.length > 0) {
      fetchUserProfiles()
    }
  }, [pairingHistory])

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
      setTimeout(() => updateAdminState({ message: null }), 5000)
    } catch (error: any) {
      updateAdminState({ message: `Failed to clear inactive history: ${error.message}` })
      setTimeout(() => updateAdminState({ message: null }), 5000)
    }
  };

  // Filter pairings into current matches and history
  const currentMatches = useMemo(() => {
    return pairingHistory.filter(pairing => pairing.active)
  }, [pairingHistory])

  const inactiveHistory = useMemo(() => {
    return pairingHistory.filter(pairing => !pairing.active)
  }, [pairingHistory])

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

  if (loading) {
    return (
      <div className="pairings-container">
        <DashboardNavigation />
        
        <main className={`pairings-content ${isCollapsed ? "sidebar-collapsed" : ""}`}>
          <div className="min-h-screen" style={{ background: '#0F1923' }}>
            <div className="container mx-auto px-4 py-8 max-w-7xl">
              {/* Admin Controls Skeleton */}
              {hasRole("ADMIN") && (
                <div className="mb-8">
                  <div className="admin-controls rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)] backdrop-blur-sm">
                    <div className="p-6 border-b border-[var(--color-border)]">
                      <div className="flex items-center gap-3 mb-4">
                        <Skeleton variant="circular" width="32px" height="32px" theme="valorant" />
                        <Skeleton width="150px" height="24px" theme="valorant" />
                      </div>
                    </div>
                    <div className="p-6 space-y-6">
                      {/* Queue Status Skeleton */}
                      <div className="p-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)] backdrop-blur-sm">
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
              
              {/* Main Content Skeleton */}
              <div className="space-y-8">
                {/* Queue/Pairing Status Skeleton */}
                <div className="valorant-card rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)] backdrop-blur-sm">
                  <div className="p-8">
                    <div className="text-center mb-8">
                      <Skeleton width="200px" height="40px" borderRadius="9999px" className="mx-auto mb-4" theme="valorant" />
                      <Skeleton width="300px" height="32px" className="mx-auto mb-2" theme="valorant" />
                      <Skeleton width="150px" height="16px" className="mx-auto" theme="valorant" />
                    </div>
                    
                    {/* Form or Status Content Skeleton */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
                      <div className="space-y-2">
                        <Skeleton width="60px" height="16px" theme="valorant" />
                        <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                      </div>
                      <div className="space-y-2">
                        <Skeleton width="80px" height="16px" theme="valorant" />
                        <Skeleton width="100%" height="40px" borderRadius="6px" theme="valorant" />
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
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
                
                {/* Pairing History Skeleton */}
                <div className="valorant-card rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)] backdrop-blur-sm">
                  <div className="p-6 border-b border-[var(--color-border)]">
                    <div className="flex items-center gap-3">
                      <Skeleton variant="circular" width="24px" height="24px" theme="valorant" />
                      <Skeleton width="150px" height="24px" theme="valorant" />
                    </div>
                  </div>
                  <div className="p-6 space-y-4">
                    {[1, 2, 3].map((i) => (
                      <div key={i} className="flex items-center justify-between p-4 rounded-lg border border-[var(--color-border)] bg-[var(--color-container-bg)]">
                        <div className="flex items-center gap-4">
                          <Skeleton variant="circular" width="40px" height="40px" theme="valorant" />
                          <div className="space-y-2">
                            <Skeleton width="120px" height="16px" theme="valorant" />
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
              </div>
            </div>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="pairings-container">
      <DashboardNavigation />

      <main className={`pairings-content ${isCollapsed ? "sidebar-collapsed" : ""}`}>
        <ErrorBoundary>
        <div className="min-h-screen" style={{ background: '#0F1923' }}>
          <div className="container mx-auto px-4 py-8 max-w-7xl">
            {/* Admin Controls */}
            <AnimatePresence>
              {hasRole("ADMIN") && (
                <motion.div
                  initial={{ opacity: 0, y: -20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -20 }}
                  className="mb-8"
                >
                  <Card className="admin-controls">
                    <CardHeader className="pb-4">
                      <CardTitle className="flex items-center gap-3 text-[var(--color-text-primary)]">
                        <div className="p-2 bg-primary/20 rounded-lg">
                          <Settings className="h-5 w-5 text-primary" />
                        </div>
                        Admin Dashboard
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-6">
                      {/* Queue Status Display */}
                      <motion.div
                        className="p-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)] backdrop-blur-sm"
                        whileHover={{ scale: 1.02 }}
                        transition={{ type: "spring", stiffness: 300 }}
                      >
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center gap-3">
                            <motion.div
                              className={`w-4 h-4 rounded-full ${
                                isQueueEnabled ? "bg-[var(--color-success)]" : "bg-[var(--color-error)]"
                              }`}
                              animate={{ scale: [1, 1.2, 1] }}
                              transition={{ duration: 2, repeat: Number.POSITIVE_INFINITY }}
                            />
                            <span className="text-[var(--color-text-primary)] font-semibold text-lg">
                              Queue Status: {isQueueEnabled ? "Active" : "Disabled"}
                            </span>
                          </div>
                          {queueConfig && (
                            <Badge variant="outline" className="text-xs border-[var(--color-border)]">
                              Updated by {queueConfig.updatedBy}
                            </Badge>
                          )}
                        </div>
                        {queueConfig && (
                          <p className="text-[var(--color-text-secondary)] text-sm">{queueConfig.message}</p>
                        )}
                      </motion.div>

                      {/* Admin Action Buttons */}
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
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
                        </motion.div>

                        <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
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
                        </motion.div>

                        <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
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
                        </motion.div>

                        <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
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
                        </motion.div>
                      </div>

                      {/* Admin Messages */}
                      <AnimatePresence>
                        {(adminState.message || adminState.queueConfigMessage) && (
                          <motion.div
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -10 }}
                            className={`p-4 rounded-xl text-sm font-medium ${
                              (adminState.message || adminState.queueConfigMessage)?.includes("Error")
                                ? "bg-[var(--color-error)]/10 border border-[var(--color-error)]/20 text-[var(--color-error)]"
                                : "bg-[var(--color-success)]/10 border border-[var(--color-success)]/20 text-[var(--color-success)]"
                            }`}
                          >
                            {adminState.queueConfigMessage || adminState.message}
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </CardContent>
                  </Card>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Hero Section */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8 }}
              className="text-center mb-12"
            >
              <div className="relative">
                <motion.h1
                  className="pairings-hero-title text-4xl md:text-5xl text-primary mb-4"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.2 }}
                >
                  Pairings
                </motion.h1>
              </div>
              <motion.p
                className="text-xl text-[var(--color-text-secondary)] max-w-2xl mx-auto leading-relaxed"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.4 }}
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
                  <div className="rounded-xl p-6 backdrop-blur-sm" style={{ 
                    background: 'rgba(31, 39, 49, 0.3)', 
                    border: '1px solid rgba(239, 68, 68, 0.2)' 
                  }}>
                    <div className="flex items-center gap-3">
                      <AlertCircle className="h-6 w-6 text-red-400" />
                      <p className="text-red-400 font-medium">{error}</p>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Main Content Grid */}
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
              {/* Left Column - Current Status & Queue */}
              <div className="xl:col-span-2 space-y-8">
                {/* Current Status */}
                <AnimatePresence mode="wait">
                  {currentPairing ? (
                    <>
                    <motion.div
                      key="paired"
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 20 }}
                      transition={{ duration: 0.5 }}
                    >
                      <Card className="active-pairing-card">
                        <CardHeader className="pb-4">
                          <CardTitle className="flex items-center gap-3 text-[var(--color-success)]">
                            <div className="p-2 bg-[var(--color-success)]/20 rounded-lg">
                              <UserCheck className="h-6 w-6" />
                            </div>
                            You're Matched!
                          </CardTitle>
                        </CardHeader>
                        <CardContent>
                          <div className="space-y-6">
                            {/* Partner Profile */}
                            <div className="flex items-center gap-4 p-4 bg-[var(--color-container-bg)] rounded-xl border border-[var(--color-border)]">
                              <motion.div 
                                whileHover={{ scale: 1.1 }} 
                                transition={{ type: "spring", stiffness: 300 }}
                                className="cursor-pointer"
                                onClick={(e) => {
                                  const partnerId = currentPairing?.user1Id === user?.id 
                                    ? currentPairing?.user2Id 
                                    : currentPairing?.user1Id;
                                  if (partnerId) {
                                    handleUserClick(partnerId, e);
                                  }
                                }}
                              >
                                <Avatar className="h-16 w-16 ring-2 ring-[var(--color-success)]/50">
                                  <AvatarImage
                                    src={pairedUser?.avatar || "/placeholder.svg"}
                                    alt={pairedUser?.displayName}
                                  />
                                  <AvatarFallback className="bg-[var(--color-success)]/20 text-[var(--color-success)] text-xl font-bold">
                                    {pairedUser?.displayName?.charAt(0) || "?"}
                                  </AvatarFallback>
                                </Avatar>
                              </motion.div>

                              <div className="flex-1">
                                <h3 className="text-xl font-bold text-[var(--color-text-primary)] mb-2">
                                  {pairedUser?.displayName || "Your Match"}
                                </h3>

                                {/* Match Stats */}
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                  <div className="flex items-center gap-2 p-2 bg-[var(--color-info)]/10 rounded-lg border border-[var(--color-info)]/20 hover:bg-[var(--color-info)]/20 transition-colors">
                                    <User className="h-4 w-4 text-[var(--color-info)]" />
                                    <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                      {currentPairing?.user1Id === user?.id
                                        ? currentPairing?.user2Age
                                        : currentPairing?.user1Age}
                                    </span>
                                  </div>

                                  <div className="flex items-center gap-2 p-2 bg-primary/10 rounded-lg border border-primary/20 hover:bg-primary/20 transition-colors">
                                    <Users className="h-4 w-4 text-primary" />
                                    <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                      {GENDERS.find(
                                        (g) =>
                                          g.value ===
                                          (currentPairing?.user1Id === user?.id
                                            ? currentPairing?.user2Gender
                                            : currentPairing?.user1Gender),
                                      )?.label || "Not specified"}
                                    </span>
                                  </div>

                                  <div className="flex items-center gap-2 p-2 bg-[var(--color-success)]/10 rounded-lg border border-[var(--color-success)]/20 hover:bg-[var(--color-success)]/20 transition-colors">
                                    <MapPin className="h-4 w-4 text-[var(--color-success)]" />
                                    <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                      {REGIONS.find(
                                        (r) =>
                                          r.value ===
                                          (currentPairing?.user1Id === user?.id
                                            ? currentPairing?.user2Region
                                            : currentPairing?.user1Region),
                                      )?.label || "Not specified"}
                                    </span>
                                  </div>

                                  <div className="flex items-center gap-2 p-2 bg-[var(--color-warning)]/10 rounded-lg border border-[var(--color-warning)]/20 hover:bg-[var(--color-warning)]/20 transition-colors">
                                    <Trophy className="h-4 w-4 text-[var(--color-warning)]" />
                                    <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                      {RANKS.find(
                                        (r) =>
                                          r.value ===
                                          (currentPairing?.user1Id === user?.id
                                            ? currentPairing?.user2Rank
                                            : currentPairing?.user1Rank),
                                      )?.label || "Not specified"}
                                    </span>
                                  </div>
                                </div>
                              </div>
                            </div>

                            {/* Match Details */}
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                              <div className="flex items-center gap-3 p-3 bg-[var(--color-container-bg)] rounded-lg">
                                <Calendar className="h-5 w-5 text-primary" />
                                <div>
                                  <p className="text-sm text-[var(--color-text-secondary)]">Matched</p>
                                  <p className="text-[var(--color-text-primary)] font-medium">
                                    {formatDate(currentPairing.matchedAt)}
                                  </p>
                                </div>
                              </div>
                              <div className="flex items-center gap-3 p-3 bg-[var(--color-container-bg)] rounded-lg">
                                <MessageCircle className="h-5 w-5 text-[var(--color-success)]" />
                                <div className="flex-1">
                                  <p className="text-sm text-[var(--color-text-secondary)]">Discord Channel</p>
                                  <a
                                    href="https://discord.com/channels/1161658340418523166/1381698742721187930"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-[var(--color-text-primary)] font-medium hover:text-[var(--color-success)] transition-colors duration-200 flex items-center gap-2 group"
                                  >
                                    <span>#pairing-chat</span>
                                    <svg
                                      className="h-4 w-4 opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                                      fill="none"
                                      viewBox="0 0 24 24"
                                      stroke="currentColor"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={2}
                                        d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                                      />
                                    </svg>
                                  </a>
                                </div>
                              </div>
                            </div>

                            {/* Detailed Message Metrics (Private View for Paired Users) */}
                            <div className="p-4 bg-[var(--color-container-bg)] rounded-xl border border-[var(--color-border)]">
                              <div className="flex items-center gap-3 mb-4">
                                <MessageSquare className="h-5 w-5 text-[var(--color-info)]" />
                                <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">Chat Activity</h3>
                              </div>
                              
                              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                                {/* Your Messages */}
                                <div className="text-center p-3 bg-[var(--color-success)]/10 rounded-lg border border-[var(--color-success)]/20">
                                  <div className="text-2xl font-bold text-[var(--color-success)] mb-1">
                                    {currentPairing?.user1Id === user?.id ? currentPairing?.user1MessageCount : currentPairing?.user2MessageCount}
                                  </div>
                                  <div className="text-sm text-[var(--color-text-secondary)]">Your Messages</div>
                                </div>
                                
                                {/* Partner's Messages */}
                                <div className="text-center p-3 bg-primary/10 rounded-lg border border-primary/20">
                                  <div className="text-2xl font-bold text-primary mb-1">
                                    {currentPairing?.user1Id === user?.id ? currentPairing?.user2MessageCount : currentPairing?.user1MessageCount}
                                  </div>
                                  <div className="text-sm text-[var(--color-text-secondary)]">
                                    {pairedUser?.displayName || "Partner"}'s Messages
                                  </div>
                                </div>
                                
                                {/* Total Messages */}
                                <div className="text-center p-3 bg-[var(--color-info)]/10 rounded-lg border border-[var(--color-info)]/20">
                                  <div className="text-2xl font-bold text-[var(--color-info)] mb-1">
                                    {currentPairing?.messageCount || 0}
                                  </div>
                                  <div className="text-sm text-[var(--color-text-secondary)]">Total Messages</div>
                                </div>
                                
                                {/* Voice Time */}
                                <div className="text-center p-3 bg-[var(--color-warning)]/10 rounded-lg border border-[var(--color-warning)]/20">
                                  <div className="text-2xl font-bold text-[var(--color-warning)] mb-1">
                                    {Math.floor((currentPairing?.voiceTimeMinutes || 0) / 60)}h {(currentPairing?.voiceTimeMinutes || 0) % 60}m
                                  </div>
                                  <div className="text-sm text-[var(--color-text-secondary)]">Voice Time</div>
                                </div>
                              </div>
                              
                            </div>

                            {/* Breakup Button */}
                            <div className="pt-4 border-t border-[var(--color-border)]">
                              <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                                <Button
                                  onClick={() => showBreakup()}
                                  disabled={actionLoading}
                                  variant="outline"
                                  className="w-full border-[var(--color-error)]/30 text-[var(--color-error)] hover:border-[var(--color-error)]/50 hover:bg-[var(--color-error)]/10 transition-all duration-200"
                                >
                                  <AlertCircle className="h-4 w-4 mr-2" />
                                  End This Match
                                </Button>
                              </motion.div>
                            </div>
                          </div>
                        </CardContent>
                      </Card>
                    </motion.div>

                    {/* XP Card - Show XP, Achievements, and Voice Streaks for Active Pairing */}
                    <motion.div
                      key="xp-card"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.5, delay: 0.2 }}
                    >
                      <XPCard 
                        pairingId={currentPairing.id}
                        className="mt-8"
                      />
                    </motion.div>
                    </>
                  ) : queueStatus.inQueue ? (
                    <motion.div
                      key="in-queue"
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 20 }}
                      transition={{ duration: 0.5 }}
                    >
                      <Card className="queue-status-card">
                        <CardContent className="p-8">
                          {/* Header Section */}
                          <div className="text-center mb-8">
                            <div className="inline-flex items-center gap-3 px-4 py-2 bg-[var(--color-info)]/10 rounded-full border border-[var(--color-info)]/20 mb-4">
                              <Clock className="h-5 w-5 text-[var(--color-info)]" />
                              <span className="text-[var(--color-info)] font-medium">Finding Your Match</span>
                            </div>
                            
                            <h2 className="text-3xl font-bold text-[var(--color-text-primary)] mb-2">
                              You're in Queue!
                            </h2>
                            
                            {/* Connection Status */}
                            <div className="flex items-center justify-center gap-2">
                              <div className={`w-2 h-2 rounded-full ${
                                isConnected ? "bg-[var(--color-success)]" : "bg-[var(--color-error)]"
                              }`} />
                              <span className="text-sm text-[var(--color-text-secondary)]">
                                {isConnected ? "Connected" : "Reconnecting..."}
                              </span>
                            </div>
                          </div>

                          {/* Queue Information Grid */}
                          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                            {queueStatus.queuePosition && queueStatus.totalQueueSize && (
                              <div className="text-center">
                                <div className="text-3xl font-bold text-[var(--color-info)] mb-1">
                                  {queueStatus.queuePosition}
                                </div>
                                <div className="text-sm text-[var(--color-text-secondary)]">
                                  of {queueStatus.totalQueueSize} in queue
                                </div>
                              </div>
                            )}
                            
                            {queueStatus.estimatedWaitTime && (
                              <div className="text-center">
                                <div className="text-3xl font-bold text-primary mb-1">
                                  {queueStatus.estimatedWaitTime}m
                                </div>
                                <div className="text-sm text-[var(--color-text-secondary)]">
                                  estimated wait
                                </div>
                              </div>
                            )}
                            
                            {queueStatus.queuedAt && (
                              <div className="text-center">
                                <div className="text-3xl font-bold text-[var(--color-success)] mb-1">
                                  {queueTimer}
                                </div>
                                <div className="text-sm text-[var(--color-text-secondary)]">
                                  in queue
                                </div>
                              </div>
                            )}
                          </div>

                          {/* Action Button */}
                          <div className="text-center">
                            <Button
                              variant="outline"
                              onClick={leaveQueue}
                              disabled={actionLoading}
                              className="px-8 py-3 border-[var(--color-text-tertiary)]/30 text-[var(--color-text-secondary)] hover:border-[var(--color-error)]/50 hover:text-[var(--color-error)] transition-all duration-200"
                            >
                              {actionLoading ? (
                                <Skeleton width="80px" height="16px" theme="valorant" className="mx-auto" />
                              ) : null}
                              Leave Queue
                            </Button>
                          </div>
                        </CardContent>
                      </Card>
                    </motion.div>
                  ) : null}
                </AnimatePresence>

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
                                <h3 className="text-xl font-bold text-[var(--color-text-primary)] mb-2">You're All Set!</h3>
                                <p className="text-[var(--color-text-secondary)]">
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
                                <AlertCircle className="h-16 w-16 text-[var(--color-warning)] mx-auto mb-6" />
                              </motion.div>
                              <h3 className="text-2xl font-bold text-[var(--color-warning)] mb-4">Queue Closed</h3>
                              <p className="text-[var(--color-text-secondary)] text-lg">
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

              {/* Right Column - Current Matches & Match History */}
              <div className="xl:col-span-1 space-y-8">
                {/* Current Matches - Visible to All Users */}
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.3 }}
                >
                  <Card className="valorant-card h-fit">
                    <CardHeader className="pb-4">
                      <CardTitle className="flex items-center gap-3 text-white">
                        <div className="p-2 bg-[var(--color-success)]/20 rounded-lg">
                          <UserCheck className="h-5 w-5 text-[var(--color-success)]" />
                        </div>
                        Current Matches
                        <Badge variant="outline" className="">
                          {currentMatches.length}
                        </Badge>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <PairingCardList
                        pairings={currentMatches}
                        userProfiles={userProfiles}
                        isActive={true}
                        onUserClick={handleUserClick}
                        onUnpair={handleUnpairUsers}
                        formatDate={formatDate}
                        hasAdminActions={hasRole("ADMIN")}
                        maxItems={10}
                        emptyMessage="No Active Matches"
                        emptyIcon={
                          <div className="mx-auto w-16 h-16 bg-gradient-to-br from-[var(--color-success)]/20 to-primary/20 rounded-full flex items-center justify-center mb-4">
                            <UserCheck className="h-8 w-8 text-[var(--color-success)]" />
                          </div>
                        }
                        streakData={{}}
                        levelData={{}}
                      />
                      {currentMatches.length === 0 && !currentPairing && !queueStatus.inQueue && isQueueEnabled && (
                        <motion.div
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-center"
                        >
                          <p className="text-[var(--color-text-secondary)] text-sm mb-2">
                            There are currently no active matches in the system.
                          </p>
                          <p className="text-[var(--color-text-tertiary)] text-xs">
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
                                  className="border-[var(--color-error)]/30 text-[var(--color-error)] hover:border-[var(--color-error)]/50 hover:bg-[var(--color-error)]/10"
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
                            formatDate={formatDate}
                            hasAdminActions={true}
                            maxItems={5}
                            emptyMessage="No Match History"
                            emptyIcon={
                              <div className="mx-auto w-16 h-16 bg-gradient-to-br from-primary/20 to-purple-500/20 rounded-full flex items-center justify-center mb-4">
                                <MessageCircle className="h-8 w-8 text-primary" />
                              </div>
                            }
                            streakData={{}}
                            levelData={{}}
                          />
                          {inactiveHistory.length === 0 && (
                            <motion.div
                              initial={{ opacity: 0, y: 20 }}
                              animate={{ opacity: 1, y: 0 }}
                              className="text-center"
                            >
                              <p className="text-[var(--color-text-secondary)] text-sm">
                                No ended matches to display in the history.
                              </p>
                            </motion.div>
                          )}
                        </CardContent>
                      </Card>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </div>
          </div>
        </div>

        {/* Modals */}
        <AnimatePresence>
          {modalState.showUserProfileModal && modalState.selectedUserProfile && (
            <UserProfileModal
              isOpen={modalState.showUserProfileModal}
              onClose={handleCloseUserProfileModal}
              userProfile={modalState.selectedUserProfile}
              position={modalState.userProfileModalPosition}
            />
          )}

          {modalState.showMatchModal && modalState.matchedPairing && (
            <MatchFoundModal pairing={modalState.matchedPairing} onClose={handleCloseMatchModal} />
          )}

          {modalState.showNoMatchModal && modalState.noMatchData && (
            <NoMatchFoundModal
              onClose={handleCloseNoMatchModal}
              onStayInQueue={handleStayInQueue}
              onLeaveQueue={handleLeaveQueueFromModal}
              totalInQueue={modalState.noMatchData.totalInQueue}
              message={modalState.noMatchData.message}
            />
          )}

          {modalState.showQueueRemovedModal && modalState.queueRemovedMessage && (
            <QueueRemovedModal
              message={modalState.queueRemovedMessage}
              onClose={handleCloseQueueRemovedModal}
            />
          )}

          {modalState.showBreakupModal && currentPairing && (
            <BreakupModal
              isOpen={modalState.showBreakupModal}
              onClose={() => hideBreakup()}
              onConfirm={handleBreakup}
              partnerName={pairedUser?.displayName || "your match"}
            />
          )}

          {modalState.showBreakupSuccessModal && (
            <BreakupSuccessModal
              isOpen={modalState.showBreakupSuccessModal}
              onClose={handleCloseBreakupSuccessModal}
              partnerName={pairedUser?.displayName || "your match"}
            />
          )}

          {modalState.showPartnerUnmatchedModal && (
            <PartnerUnmatchedModal
              isOpen={modalState.showPartnerUnmatchedModal}
              onClose={handleClosePartnerUnmatchedModal}
              onJoinQueue={handleJoinQueueFromPartnerModal}
              partnerName={modalState.offlineBreakupPartnerName || pairedUser?.displayName || "your match"}
            />
          )}
        </AnimatePresence>
        </ErrorBoundary>
      </main>
    </div>
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
          <CardTitle className="flex items-center gap-3 text-[var(--color-warning)]">
            <AlertCircle className="h-6 w-6" />
            Queue Disabled
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-[var(--color-text-secondary)]">{message}</p>
          <Button onClick={onClose} className="w-full valorant-button-primary">
            <Heart className="mr-2 h-5 w-5" />
            Understood
          </Button>
        </CardContent>
      </Card>
    </motion.div>
  </div>
)
