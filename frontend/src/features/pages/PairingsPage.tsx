"use client"

import type React from "react"

import { useState, useEffect, useCallback, useMemo } from "react"
import { useAuth } from "@/contexts/auth/useAuth"
import { usePairings } from "@/hooks/usePairings"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/valorant/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/valorant/select"
import { Heart, Users, Trophy, MessageCircle, Settings, User, MapPin, Calendar, AlertCircle, Clock, Zap, Star, UserCheck, Activity, ChevronRight, Trash2, X } from 'lucide-react'
import { motion, AnimatePresence } from "framer-motion"
import type { JoinQueueRequestDTO, PairingDTO } from "@/config/pairingService"
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

const REGIONS = [
  { value: "NA_EAST", label: "NA East" },
  { value: "NA_WEST", label: "NA West" },
  { value: "NA_CENTRAL", label: "NA Central" },
  { value: "EU", label: "Europe" },
  { value: "AP", label: "Asia Pacific" },
  { value: "KR", label: "Korea" },
  { value: "LATAM", label: "Latin America" },
  { value: "BR", label: "Brazil" },
]

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
]

const GENDERS = [
  { value: "MALE", label: "Male" },
  { value: "FEMALE", label: "Female" },
  { value: "NON_BINARY", label: "Non-Binary" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" },
]

// Enhanced Queue Join Form with better UX
const QueueJoinForm = ({
  onJoinQueue,
  loading,
}: {
  onJoinQueue: (data: JoinQueueRequestDTO) => Promise<void>
  loading: boolean
}) => {
  const [age, setAge] = useState<string>("")
  const [region, setRegion] = useState<string>("")
  const [rank, setRank] = useState<string>("")
  const [gender, setGender] = useState<string>("")

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()

      const ageNum = Number.parseInt(age)
      if (!ageNum || ageNum < 13 || ageNum > 100) {
        throw new Error("Please enter a valid age between 13 and 100")
      }

      if (!region || !rank || !gender) {
        throw new Error("Please fill in all fields")
      }

      await onJoinQueue({
        userId: "",
        age: ageNum,
        region: region as any,
        rank: rank as any,
        gender: gender as any,
      })

      setAge("")
      setRegion("")
      setRank("")
      setGender("")
    },
    [age, region, rank, gender, onJoinQueue],
  )

  const isFormValid = age && region && rank && gender

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
                  value={age}
                  onChange={(e) => setAge(e.target.value)}
                  className="bg-[var(--color-container-bg)] border-[var(--color-border)] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-primary focus:ring-1 focus:ring-primary/20"
                  min="13"
                  max="100"
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="gender" className="text-sm font-medium text-[var(--color-text-primary)]">
                  Gender
                </Label>
                <Select value={gender} onValueChange={setGender} required>
                  <SelectTrigger className="bg-[var(--color-container-bg)] border-[var(--color-border)] text-[var(--color-text-primary)]">
                    <SelectValue placeholder="Select gender" />
                  </SelectTrigger>
                  <SelectContent>
                    {GENDERS.map((g) => (
                      <SelectItem key={g.value} value={g.value}>
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
                <Select value={region} onValueChange={setRegion} required>
                  <SelectTrigger className="valorant-select">
                    <SelectValue placeholder="Select region" />
                  </SelectTrigger>
                  <SelectContent>
                    {REGIONS.map((reg) => (
                      <SelectItem key={reg.value} value={reg.value}>
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
                <Select value={rank} onValueChange={setRank} required>
                  <SelectTrigger className="valorant-select">
                    <SelectValue placeholder="Select rank" />
                  </SelectTrigger>
                  <SelectContent>
                    {RANKS.map((r) => (
                      <SelectItem key={r.value} value={r.value}>
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
}

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
    clearInactiveHistory
  } = usePairings()
  const { isConnected } = useQueueUpdates()
  const { pairingUpdate, clearUpdate } = usePairingUpdates()

  const [adminActionLoading, setAdminActionLoading] = useState(false)
  const [adminMessage, setAdminMessage] = useState<string | null>(null)
  const [showMatchModal, setShowMatchModal] = useState(false)
  const [matchedPairing, setMatchedPairing] = useState<PairingDTO | null>(null)
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({})
  const [selectedUserProfile, setSelectedUserProfile] = useState<UserProfileDTO | null>(null)
  const [showUserProfileModal, setShowUserProfileModal] = useState(false)
  const [userProfileModalPosition, setUserProfileModalPosition] = useState<{ x: number; y: number } | null>(null)
  const [showNoMatchModal, setShowNoMatchModal] = useState(false)
  const [noMatchData, setNoMatchData] = useState<{ totalInQueue?: number; message?: string } | null>(null)

  const [isCollapsed, setIsCollapsed] = useState(() => {
    const savedState = localStorage.getItem("sidebar-collapsed")
    return savedState ? JSON.parse(savedState) : false
  })

  const [queueTimer, setQueueTimer] = useState<string>('0s')

  useEffect(() => {
    let interval: NodeJS.Timeout
    let startTime: number

    if (queueStatus.inQueue) {
      startTime = Date.now()
      setQueueTimer('0s') // Reset to 0 when entering queue
      
      interval = setInterval(() => {
        const elapsed = Math.floor((Date.now() - startTime) / 1000)
        
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
      }, 1000)
    } else {
      setQueueTimer('0s')
    }

    return () => {
      if (interval) clearInterval(interval)
    }
  }, [queueStatus.inQueue])

  useEffect(() => {
    const handleSidebarStateChange = (event: CustomEvent) => {
      setIsCollapsed(event.detail.collapsed)
    }

    window.addEventListener("sidebarStateChange", handleSidebarStateChange as EventListener)
    return () => {
      window.removeEventListener("sidebarStateChange", handleSidebarStateChange as EventListener)
    }
  }, [])

  const formatDate = useMemo(
    () => (dateString: string) => {
      return new Intl.DateTimeFormat("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(dateString))
    },
    [],
  )

  const { queueConfig, isQueueEnabled } = useQueueConfig()
  const [queueConfigLoading, setQueueConfigLoading] = useState(false)
  const [queueConfigMessage, setQueueConfigMessage] = useState<string | null>(null)

  const handleJoinQueue = useCallback(
    async (queueData: Omit<JoinQueueRequestDTO, "userId">) => {
      if (!user?.id) {
        throw new Error("User authentication required")
      }

      try {
        await joinQueue({
          ...queueData,
          userId: user.id,
        })
      } catch (err: any) {
        const errorMessage = err?.message || "Failed to join matchmaking queue"
        console.error("Queue join error:", err)
        throw new Error(errorMessage)
      }
    },
    [user?.id, joinQueue],
  )

  const handleAdminMatchmaking = async () => {
    try {
      setAdminActionLoading(true)
      setAdminMessage(null)

      const newPairings = await performMatchmaking()

      setAdminMessage(`Successfully created ${newPairings.length} new pairings! Notifications will be sent shortly...`)
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || "Matchmaking failed"
      setAdminMessage(`Error: ${errorMessage}`)
      console.error("Admin matchmaking error:", err)
    } finally {
      setAdminActionLoading(false)
    }
  }

  const handleDeleteAllPairings = async () => {
    if (!confirm("Are you sure you want to delete ALL active pairings? This action cannot be undone.")) {
      return
    }

    try {
      setAdminActionLoading(true)
      setAdminMessage(null)

      const result = await deleteAllPairings()
      setAdminMessage(`Successfully deleted ${result.deletedCount} active pairing(s)!`)

      window.location.reload()

      setTimeout(() => setAdminMessage(null), 5000)
    } catch (error: any) {
      setAdminMessage(`Failed to delete pairings: ${error.message}`)
      setTimeout(() => setAdminMessage(null), 5000)
    } finally {
      setAdminActionLoading(false)
    }
  }

  useEffect(() => {
    if (pairingUpdate) {
      if (pairingUpdate.eventType === "MATCH_FOUND" && pairingUpdate.pairing) {
        console.log("[PairingsPage] Match found, showing modal:", pairingUpdate)
        setMatchedPairing(pairingUpdate.pairing)
        setShowMatchModal(true)
        refreshData()
        clearUpdate()
      } else if (pairingUpdate.eventType === "NO_MATCH_FOUND") {
        console.log("[PairingsPage] No match found, showing modal:", pairingUpdate)
        setNoMatchData({
          totalInQueue: pairingUpdate.totalInQueue,
          message: pairingUpdate.message
        })
        setShowNoMatchModal(true)
        clearUpdate()
      }
    }
  }, [pairingUpdate, refreshData, clearUpdate])

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
        setSelectedUserProfile(profile)
        setUserProfileModalPosition({ x: event.clientX, y: event.clientY })
        setShowUserProfileModal(true)
      }
    },
    [userProfiles],
  )

  const handleCloseUserProfileModal = useCallback(() => {
    setShowUserProfileModal(false)
    setSelectedUserProfile(null)
    setUserProfileModalPosition(null)
  }, [])

  const handleCloseMatchModal = () => {
    console.log("[PairingsPage] Closing match modal")
    setShowMatchModal(false)
    setMatchedPairing(null)
  }

  const handleEnableQueue = async () => {
    try {
      setQueueConfigLoading(true)
      setQueueConfigMessage(null)

      const result = await enableQueue()
      setQueueConfigMessage(`Queue enabled successfully: ${result.message}`)

      setTimeout(() => setQueueConfigMessage(null), 5000)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || "Failed to enable queue"
      setQueueConfigMessage(`Error: ${errorMessage}`)
      setTimeout(() => setQueueConfigMessage(null), 5000)
    } finally {
      setQueueConfigLoading(false)
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
      setQueueConfigLoading(true)
      setQueueConfigMessage(null)

      const result = await disableQueue()
      setQueueConfigMessage(`Queue disabled successfully: ${result.message}`)

      setTimeout(() => setQueueConfigMessage(null), 5000)
    } catch (error: any) {
      const errorMessage = error?.response?.data?.message || error?.message || "Failed to disable queue"
      setQueueConfigMessage(`Error: ${errorMessage}`)
      setTimeout(() => setQueueConfigMessage(null), 5000)
    } finally {
      setQueueConfigLoading(false)
    }
  }

  const handleCloseNoMatchModal = () => {
    console.log("[PairingsPage] Closing no match modal")
    setShowNoMatchModal(false)
    setNoMatchData(null)
  }

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
      setAdminMessage("Users unpaired successfully! They remain blacklisted from future matches.");
      setTimeout(() => setAdminMessage(null), 5000);
    } catch (error: any) {
      setAdminMessage(`Failed to unpair users: ${error.message}`);
      setTimeout(() => setAdminMessage(null), 5000);
    }
  };

  const handleDeletePairing = async (pairingId: number, event: React.MouseEvent) => {
    event.stopPropagation();
    
    if (!confirm("Are you sure you want to permanently delete this pairing record? The users will remain blacklisted and cannot match again. This action cannot be undone.")) {
      return;
    }

    try {
      await deletePairing(pairingId);
      setAdminMessage("Pairing record permanently deleted! Users remain blacklisted from future matches.");
      setTimeout(() => setAdminMessage(null), 5000);
    } catch (error: any) {
      setAdminMessage(`Failed to delete pairing: ${error.message}`);
      setTimeout(() => setAdminMessage(null), 5000);
    }
  };

  const handleClearInactiveHistory = async () => {
    if (!confirm("Are you sure you want to permanently delete ALL inactive pairing records? Users will remain blacklisted and cannot match again. This action cannot be undone.")) {
      return;
    }

    try {
      const result = await clearInactiveHistory();
      setAdminMessage(`Successfully deleted ${result.deletedCount} inactive pairing record(s)! All users remain blacklisted.`);
      setTimeout(() => setAdminMessage(null), 5000);
    } catch (error: any) {
      setAdminMessage(`Failed to clear inactive history: ${error.message}`);
      setTimeout(() => setAdminMessage(null), 5000);
    }
  };

  // Filter pairings into current matches and history
  const currentMatches = useMemo(() => {
    return pairingHistory.filter(pairing => pairing.active)
  }, [pairingHistory])

  const inactiveHistory = useMemo(() => {
    return pairingHistory.filter(pairing => !pairing.active)
  }, [pairingHistory])

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
                            disabled={queueConfigLoading || isQueueEnabled}
                            variant={isQueueEnabled ? "outline" : "default"}
                            className="w-full h-12 valorant-button-success"
                          >
                            {queueConfigLoading ? (
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
                            disabled={queueConfigLoading || !isQueueEnabled}
                            variant="destructive"
                            className="w-full h-12"
                          >
                            {queueConfigLoading ? (
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
                            disabled={adminActionLoading}
                            className="w-full h-12 valorant-button-primary"
                          >
                            {adminActionLoading ? (
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
                            disabled={adminActionLoading}
                            variant="destructive"
                            className="w-full h-12"
                          >
                            {adminActionLoading ? (
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
                        {(adminMessage || queueConfigMessage) && (
                          <motion.div
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -10 }}
                            className={`p-4 rounded-xl text-sm font-medium ${
                              (adminMessage || queueConfigMessage)?.includes("Error")
                                ? "bg-[var(--color-error)]/10 border border-[var(--color-error)]/20 text-[var(--color-error)]"
                                : "bg-[var(--color-success)]/10 border border-[var(--color-success)]/20 text-[var(--color-success)]"
                            }`}
                          >
                            {queueConfigMessage || adminMessage}
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
                                <div>
                                  <p className="text-sm text-[var(--color-text-secondary)]">Discord Channel</p>
                                  <p className="text-[var(--color-text-primary)] font-medium">
                                    #{currentPairing.discordChannelId}
                                  </p>
                                </div>
                              </div>
                            </div>
                          </div>
                        </CardContent>
                      </Card>
                    </motion.div>
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
                      {currentMatches.length > 0 ? (
                        currentMatches.slice(0, 5).map((pairing, index) => {
                          const user1Profile = userProfiles[pairing.user1Id]
                          const user2Profile = userProfiles[pairing.user2Id]

                          return (
                            <motion.div
                              key={pairing.id}
                              initial={{ opacity: 0, y: 20 }}
                              animate={{ opacity: 1, y: 0 }}
                              transition={{ delay: index * 0.1 }}
                              className="group p-4 rounded-xl border transition-all duration-300 hover:border-[var(--color-success)]/30 relative"
                              style={{ 
                                background: 'rgba(31, 39, 49, 0.4)', 
                                borderColor: 'rgba(34, 197, 94, 0.1)' 
                              }}
                            >
                              {/* Admin Unpair Button */}
                              {hasRole("ADMIN") && (
                                <motion.button
                                  whileHover={{ scale: 1.1 }}
                                  whileTap={{ scale: 0.9 }}
                                  onClick={(e) => handleUnpairUsers(pairing.id, e)}
                                  className="absolute top-2 right-2 p-1 rounded-full bg-[var(--color-warning)]/20 border border-[var(--color-warning)]/30 text-[var(--color-warning)] hover:bg-[var(--color-warning)]/30 transition-colors opacity-0 group-hover:opacity-100 z-10"
                                  title="Unpair these users (keeps blacklist)"
                                >
                                  <X className="h-3 w-3" />
                                </motion.button>
                              )}
                              
                              <div className="flex items-center justify-between mb-3">
                                <div className="flex items-center gap-3">
                                  {/* User 1 */}
                                  <motion.div
                                    className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
                                    onClick={(e) => handleUserClick(pairing.user1Id, e)}
                                    whileHover={{ scale: 1.05 }}
                                  >
                                    <Avatar className="h-8 w-8 ring-2 ring-[var(--color-success)]/30">
                                      <AvatarImage src={user1Profile?.avatar || "/placeholder.svg"} />
                                      <AvatarFallback className="bg-[var(--color-success)]/20 text-[var(--color-success)]">
                                        {user1Profile?.displayName?.[0] || user1Profile?.username?.[0] || "?"}
                                      </AvatarFallback>
                                    </Avatar>
                                    <span className="text-[var(--color-text-primary)] font-medium text-sm">
                                      {user1Profile?.displayName || user1Profile?.username || "Unknown"}
                                    </span>
                                  </motion.div>

                                  <Heart className="h-4 w-4 text-[var(--color-success)]" />

                                  {/* User 2 */}
                                  <motion.div
                                    className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
                                    onClick={(e) => handleUserClick(pairing.user2Id, e)}
                                    whileHover={{ scale: 1.05 }}
                                  >
                                    <Avatar className="h-8 w-8 ring-2 ring-[var(--color-success)]/30">
                                      <AvatarImage src={user2Profile?.avatar || "/placeholder.svg"} />
                                      <AvatarFallback className="bg-[var(--color-success)]/20 text-[var(--color-success)]">
                                        {user2Profile?.displayName?.[0] || user2Profile?.username?.[0] || "?"}
                                      </AvatarFallback>
                                    </Avatar>
                                    <span className="text-[var(--color-text-primary)] font-medium text-sm">
                                      {user2Profile?.displayName || user2Profile?.username || "Unknown"}
                                    </span>
                                  </motion.div>
                                </div>

                                <ChevronRight className="h-4 w-4 text-[var(--color-text-tertiary)] group-hover:text-[var(--color-success)] transition-colors" />
                              </div>

                              <div className="flex items-center justify-between text-xs">
                                <div className="flex items-center gap-2">
                                  <Badge
                                    variant="default"
                                    className="text-xs bg-[var(--color-success)]/20 text-[var(--color-success)] border-[var(--color-success)]/30"
                                  >
                                    Active
                                  </Badge>
                                  <Badge variant="outline" className="text-xs border-[var(--color-success)]/30 text-[var(--color-success)]">
                                    <Star className="h-3 w-3 mr-1" />
                                    {pairing.compatibilityScore}%
                                  </Badge>
                                </div>
                                <div className="text-[var(--color-text-tertiary)]">
                                  <p>{formatDate(pairing.matchedAt)}</p>
                                  <p className="text-right">{pairing.activeDays} days</p>
                                </div>
                              </div>
                            </motion.div>
                          )
                        })
                      ) : (
                        <motion.div
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-center py-12"
                        >
                          <div className="mx-auto w-16 h-16 bg-gradient-to-br from-[var(--color-success)]/20 to-primary/20 rounded-full flex items-center justify-center mb-4">
                            <UserCheck className="h-8 w-8 text-[var(--color-success)]" />
                          </div>
                          <h3 className="text-lg font-semibold text-[var(--color-text-primary)] mb-2">
                            No Active Matches
                          </h3>
                          <p className="text-[var(--color-text-secondary)] text-sm">
                            There are currently no active matches in the system.
                          </p>
                          {!currentPairing && !queueStatus.inQueue && isQueueEnabled && (
                            <p className="text-[var(--color-text-tertiary)] text-xs mt-2">
                              Join the queue to find your match!
                            </p>
                          )}
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
                          {inactiveHistory.length > 0 ? (
                            inactiveHistory.slice(0, 5).map((pairing, index) => {
                              const user1Profile = userProfiles[pairing.user1Id]
                              const user2Profile = userProfiles[pairing.user2Id]

                              return (
                                <motion.div
                                  key={pairing.id}
                                  initial={{ opacity: 0, y: 20 }}
                                  animate={{ opacity: 1, y: 0 }}
                                  transition={{ delay: index * 0.1 }}
                                  className="group p-4 rounded-xl border transition-all duration-300 hover:border-primary/30 relative"
                                  style={{ 
                                    background: 'rgba(31, 39, 49, 0.4)', 
                                    borderColor: 'rgba(255, 255, 255, 0.05)' 
                                  }}
                                >
                                  {/* Admin Delete Button */}
                                  <motion.button
                                    whileHover={{ scale: 1.1 }}
                                    whileTap={{ scale: 0.9 }}
                                    onClick={(e) => handleDeletePairing(pairing.id, e)}
                                    className="absolute top-2 right-2 p-1 rounded-full bg-[var(--color-error)]/20 border border-[var(--color-error)]/30 text-[var(--color-error)] hover:bg-[var(--color-error)]/30 transition-colors opacity-0 group-hover:opacity-100 z-10"
                                    title="Permanently delete this pairing record"
                                  >
                                    <X className="h-3 w-3" />
                                  </motion.button>
                                  
                                  <div className="flex items-center justify-between mb-3">
                                    <div className="flex items-center gap-3">
                                      {/* User 1 */}
                                      <motion.div
                                        className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
                                        onClick={(e) => handleUserClick(pairing.user1Id, e)}
                                        whileHover={{ scale: 1.05 }}
                                      >
                                        <Avatar className="h-8 w-8 ring-2 ring-primary/30">
                                          <AvatarImage src={user1Profile?.avatar || "/placeholder.svg"} />
                                          <AvatarFallback className="bg-primary/20 text-primary">
                                            {user1Profile?.displayName?.[0] || user1Profile?.username?.[0] || "?"}
                                          </AvatarFallback>
                                        </Avatar>
                                        <span className="text-[var(--color-text-primary)] font-medium text-sm">
                                          {user1Profile?.displayName || user1Profile?.username || "Unknown"}
                                        </span>
                                      </motion.div>

                                      <Heart className="h-4 w-4 text-primary" />

                                      {/* User 2 */}
                                      <motion.div
                                        className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
                                        onClick={(e) => handleUserClick(pairing.user2Id, e)}
                                        whileHover={{ scale: 1.05 }}
                                      >
                                        <Avatar className="h-8 w-8 ring-2 ring-primary/30">
                                          <AvatarImage src={user2Profile?.avatar || "/placeholder.svg"} />
                                          <AvatarFallback className="bg-primary/20 text-primary">
                                            {user2Profile?.displayName?.[0] || user2Profile?.username?.[0] || "?"}
                                          </AvatarFallback>
                                        </Avatar>
                                        <span className="text-[var(--color-text-primary)] font-medium text-sm">
                                          {user2Profile?.displayName || user2Profile?.username || "Unknown"}
                                        </span>
                                      </motion.div>
                                    </div>

                                    <ChevronRight className="h-4 w-4 text-[var(--color-text-tertiary)] group-hover:text-primary transition-colors" />
                                  </div>

                                  <div className="flex items-center justify-between text-xs">
                                    <div className="flex items-center gap-2">
                                      <Badge
                                        variant="secondary"
                                        className="text-xs"
                                      >
                                        Ended
                                      </Badge>
                                      <Badge variant="outline" className="text-xs border-primary/30 text-primary">
                                        <Star className="h-3 w-3 mr-1" />
                                        {pairing.compatibilityScore}%
                                      </Badge>
                                    </div>
                                    <div className="text-[var(--color-text-tertiary)]">
                                      <p>{formatDate(pairing.matchedAt)}</p>
                                      <p className="text-right">{pairing.activeDays} days</p>
                                    </div>
                                  </div>
                                </motion.div>
                              )
                            })
                          ) : (
                            <motion.div
                              initial={{ opacity: 0, y: 20 }}
                              animate={{ opacity: 1, y: 0 }}
                              className="text-center py-12"
                            >
                              <div className="mx-auto w-16 h-16 bg-gradient-to-br from-primary/20 to-purple-500/20 rounded-full flex items-center justify-center mb-4">
                                <MessageCircle className="h-8 w-8 text-primary" />
                              </div>
                              <h3 className="text-lg font-semibold text-[var(--color-text-primary)] mb-2">
                                No Match History
                              </h3>
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
          {showUserProfileModal && selectedUserProfile && (
            <UserProfileModal
              isOpen={showUserProfileModal}
              onClose={handleCloseUserProfileModal}
              userProfile={selectedUserProfile}
              position={userProfileModalPosition}
            />
          )}

          {showMatchModal && matchedPairing && (
            <MatchFoundModal pairing={matchedPairing} onClose={handleCloseMatchModal} />
          )}

          {showNoMatchModal && noMatchData && (
            <NoMatchFoundModal
              onClose={handleCloseNoMatchModal}
              onStayInQueue={handleStayInQueue}
              onLeaveQueue={handleLeaveQueueFromModal}
              totalInQueue={noMatchData.totalInQueue}
              message={noMatchData.message}
            />
          )}
        </AnimatePresence>
      </main>
    </div>
  )
}
