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
import { Loader2, Heart, Users, Trophy, MessageCircle, Settings, User, MapPin, Calendar, AlertCircle, Clock, Zap, Star, UserCheck, Activity, ChevronRight } from 'lucide-react'
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
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/valorant/tooltip"

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
            Join the Queue
          </CardTitle>
          <p className="text-slate-400 text-sm">Find your perfect match and start your journey</p>
        </CardHeader>
        <CardContent className="space-y-6">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.1 }}>
                <Label htmlFor="age" className="text-slate-200 font-medium mb-2 block">
                  Age
                </Label>
                <Input
                  id="age"
                  type="number"
                  placeholder="Your age"
                  value={age}
                  onChange={(e) => setAge(e.target.value)}
                  min="13"
                  max="100"
                  required
                  className="valorant-input"
                />
              </motion.div>

              <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2 }}>
                <Label htmlFor="gender" className="text-slate-200 font-medium mb-2 block">
                  Gender
                </Label>
                <Select value={gender} onValueChange={setGender} required>
                  <SelectTrigger className="valorant-select">
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
              </motion.div>
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
                  <>
                    <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                    Joining Queue...
                  </>
                ) : (
                  <>
                    <Heart className="mr-2 h-5 w-5" />
                    Find My Match
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

  const [isCollapsed, setIsCollapsed] = useState(() => {
    const savedState = localStorage.getItem("sidebar-collapsed")
    return savedState ? JSON.parse(savedState) : false
  })

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
    if (pairingUpdate && pairingUpdate.eventType === "MATCH_FOUND" && pairingUpdate.pairing) {
      console.log("[PairingsPage] Match found, showing modal:", pairingUpdate)
      setMatchedPairing(pairingUpdate.pairing)
      setShowMatchModal(true)
      refreshData()
      clearUpdate()
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

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[var(--color-bg-gradient-from)]">
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.5 }}
          className="text-center"
        >
          <div className="relative">
            <Loader2 className="h-12 w-12 animate-spin text-primary mx-auto mb-4" />
            <div className="absolute inset-0 h-12 w-12 bg-primary/20 rounded-full animate-pulse mx-auto"></div>
          </div>
          <p className="text-[var(--color-text-primary)] text-lg">Loading your matches...</p>
        </motion.div>
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
                              <Loader2 className="h-4 w-4 animate-spin" />
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
                              <Loader2 className="h-4 w-4 animate-spin" />
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
                              <Loader2 className="h-4 w-4 animate-spin" />
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
                              <Loader2 className="h-4 w-4 animate-spin" />
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
                  className="text-5xl md:text-7xl font-bold text-primary mb-4"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.2 }}
                >
                  Don't Catch Feelings
                </motion.h1>
                <motion.div
                  className="absolute -top-4 -right-4 text-4xl"
                  animate={{ rotate: [0, 10, -10, 0] }}
                  transition={{ duration: 2, repeat: Number.POSITIVE_INFINITY }}
                >
                  💕
                </motion.div>
              </div>
              <motion.p
                className="text-xl text-[var(--color-text-secondary)] max-w-2xl mx-auto leading-relaxed"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.4 }}
              >
                Find your perfect gaming partner and see if you can keep it casual.
                <span className="text-primary font-semibold"> Challenge accepted?</span>
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
                            <motion.div
                              animate={{ scale: [1, 1.2, 1] }}
                              transition={{ duration: 1.5, repeat: Number.POSITIVE_INFINITY }}
                            >
                              💚
                            </motion.div>
                          </CardTitle>
                        </CardHeader>
                        <CardContent>
                          <div className="space-y-6">
                            {/* Partner Profile */}
                            <div className="flex items-center gap-4 p-4 bg-[var(--color-container-bg)] rounded-xl border border-[var(--color-border)]">
                              <motion.div whileHover={{ scale: 1.1 }} transition={{ type: "spring", stiffness: 300 }}>
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
                                  <TooltipProvider>
                                    <Tooltip>
                                      <TooltipTrigger>
                                        <div className="flex items-center gap-2 p-2 bg-[var(--color-info)]/10 rounded-lg border border-[var(--color-info)]/20 hover:bg-[var(--color-info)]/20 transition-colors">
                                          <User className="h-4 w-4 text-[var(--color-info)]" />
                                          <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                            {currentPairing?.user1Id === user?.id
                                              ? currentPairing?.user2Age
                                              : currentPairing?.user1Age}
                                          </span>
                                        </div>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>
                                          Age:{" "}
                                          {currentPairing?.user1Id === user?.id
                                            ? currentPairing?.user2Age
                                            : currentPairing?.user1Age}
                                        </p>
                                      </TooltipContent>
                                    </Tooltip>
                                  </TooltipProvider>

                                  <TooltipProvider>
                                    <Tooltip>
                                      <TooltipTrigger>
                                        <div className="flex items-center gap-2 p-2 bg-primary/10 rounded-lg border border-primary/20 hover:bg-primary/20 transition-colors">
                                          <div className="h-4 w-4 bg-primary rounded-full" />
                                          <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                            {GENDERS.find(
                                              (g) =>
                                                g.value ===
                                                (currentPairing?.user1Id === user?.id
                                                  ? currentPairing?.user2Gender
                                                  : currentPairing?.user1Gender),
                                            )?.label?.slice(0, 1) || "N"}
                                          </span>
                                        </div>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>
                                          Gender:{" "}
                                          {GENDERS.find(
                                            (g) =>
                                              g.value ===
                                              (currentPairing?.user1Id === user?.id
                                                ? currentPairing?.user2Gender
                                                : currentPairing?.user1Gender),
                                          )?.label || "Not specified"}
                                        </p>
                                      </TooltipContent>
                                    </Tooltip>
                                  </TooltipProvider>

                                  <TooltipProvider>
                                    <Tooltip>
                                      <TooltipTrigger>
                                        <div className="flex items-center gap-2 p-2 bg-[var(--color-success)]/10 rounded-lg border border-[var(--color-success)]/20 hover:bg-[var(--color-success)]/20 transition-colors">
                                          <MapPin className="h-4 w-4 text-[var(--color-success)]" />
                                          <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                            {REGIONS.find(
                                              (r) =>
                                                r.value ===
                                                (currentPairing?.user1Id === user?.id
                                                  ? currentPairing?.user2Region
                                                  : currentPairing?.user1Region),
                                            )?.label?.slice(0, 2) || "N/A"}
                                          </span>
                                        </div>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>
                                          Region:{" "}
                                          {REGIONS.find(
                                            (r) =>
                                              r.value ===
                                              (currentPairing?.user1Id === user?.id
                                                ? currentPairing?.user2Region
                                                : currentPairing?.user1Region),
                                          )?.label || "Not specified"}
                                        </p>
                                      </TooltipContent>
                                    </Tooltip>
                                  </TooltipProvider>

                                  <TooltipProvider>
                                    <Tooltip>
                                      <TooltipTrigger>
                                        <div className="flex items-center gap-2 p-2 bg-[var(--color-warning)]/10 rounded-lg border border-[var(--color-warning)]/20 hover:bg-[var(--color-warning)]/20 transition-colors">
                                          <Trophy className="h-4 w-4 text-[var(--color-warning)]" />
                                          <span className="text-sm font-medium text-[var(--color-text-secondary)]">
                                            {RANKS.find(
                                              (r) =>
                                                r.value ===
                                                (currentPairing?.user1Id === user?.id
                                                  ? currentPairing?.user2Rank
                                                  : currentPairing?.user1Rank),
                                            )?.label?.slice(0, 3) || "N/A"}
                                          </span>
                                        </div>
                                      </TooltipTrigger>
                                      <TooltipContent>
                                        <p>
                                          Rank:{" "}
                                          {RANKS.find(
                                            (r) =>
                                              r.value ===
                                              (currentPairing?.user1Id === user?.id
                                                ? currentPairing?.user2Rank
                                                : currentPairing?.user1Rank),
                                          )?.label || "Not specified"}
                                        </p>
                                      </TooltipContent>
                                    </Tooltip>
                                  </TooltipProvider>
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
                        <CardHeader className="pb-4">
                          <CardTitle className="flex items-center gap-3 text-[var(--color-info)]">
                            <div className="p-2 bg-[var(--color-info)]/20 rounded-lg">
                              <Clock className="h-6 w-6" />
                            </div>
                            Finding Your Match...
                          </CardTitle>
                        </CardHeader>
                        <CardContent>
                          <div className="text-center py-8">
                            <motion.div
                              animate={{ rotate: 360 }}
                              transition={{ duration: 2, repeat: Number.POSITIVE_INFINITY, ease: "linear" }}
                              className="mx-auto mb-6"
                            >
                              <Users className="h-16 w-16 text-[var(--color-info)]" />
                            </motion.div>

                            <h3 className="text-2xl font-bold text-[var(--color-text-primary)] mb-4">
                              You're in the queue!
                            </h3>

                            <div className="flex items-center justify-center gap-2 mb-6">
                              <motion.div
                                className={`w-3 h-3 rounded-full ${
                                  isConnected ? "bg-[var(--color-success)]" : "bg-[var(--color-error)]"
                                }`}
                                animate={{ scale: [1, 1.2, 1] }}
                                transition={{ duration: 1.5, repeat: Number.POSITIVE_INFINITY }}
                              />
                              <span className="text-sm text-[var(--color-text-secondary)]">
                                {isConnected ? "Connected - Live updates active" : "Reconnecting..."}
                              </span>
                            </div>

                            <div className="space-y-4 mb-8">
                              {queueStatus.queuePosition && queueStatus.totalQueueSize && (
                                <div className="p-4 bg-[var(--color-container-bg)] rounded-xl">
                                  <p className="text-lg text-[var(--color-text-secondary)]">
                                    Position:{" "}
                                    <span className="font-bold text-[var(--color-info)] text-2xl">
                                      {queueStatus.queuePosition}
                                    </span>{" "}
                                    of {queueStatus.totalQueueSize}
                                  </p>
                                </div>
                              )}
                              {queueStatus.estimatedWaitTime && (
                                <div className="p-4 bg-[var(--color-container-bg)] rounded-xl">
                                  <p className="text-lg text-[var(--color-text-secondary)]">
                                    Estimated wait:{" "}
                                    <span className="font-bold text-primary text-2xl">
                                      {queueStatus.estimatedWaitTime}
                                    </span>{" "}
                                    minutes
                                  </p>
                                </div>
                              )}
                              {queueStatus.queuedAt && (
                                <p className="text-sm text-[var(--color-text-tertiary)]">
                                  Queued since {formatDate(queueStatus.queuedAt)}
                                </p>
                              )}
                            </div>

                            <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                              <Button
                                variant="outline"
                                onClick={leaveQueue}
                                disabled={actionLoading}
                                className="valorant-button-secondary"
                              >
                                {actionLoading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                                Leave Queue
                              </Button>
                            </motion.div>
                          </div>
                        </CardContent>
                      </Card>
                    </motion.div>
                  ) : null}
                </AnimatePresence>

                {/* Queue Join Section */}
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
                          <h3 className="text-2xl font-bold text-[var(--color-warning)] mb-4">Queue Temporarily Closed</h3>
                          <p className="text-[var(--color-text-secondary)] text-lg">
                            The matchmaking queue is currently disabled. Check back next week for new matches!
                          </p>
                        </CardContent>
                      </Card>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              {/* Right Column - Pairing History */}
              {pairingHistory.length > 0 && (
                <motion.div
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.3 }}
                  className="xl:col-span-1"
                >
                  <Card className="valorant-card h-fit">
                    <CardHeader className="pb-4">
                      <CardTitle className="flex items-center gap-3 text-white">
                        <div className="p-2 bg-primary/20 rounded-lg">
                          <MessageCircle className="h-5 w-5 text-primary" />
                        </div>
                        Match History
                        <Badge variant="outline" className="ml-auto">
                          {pairingHistory.length}
                        </Badge>
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      {pairingHistory.slice(0, 5).map((pairing, index) => {
                        const user1Profile = userProfiles[pairing.user1Id]
                        const user2Profile = userProfiles[pairing.user2Id]

                        return (
                          <motion.div
                            key={pairing.id}
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: index * 0.1 }}
                            className="group p-4 rounded-xl border transition-all duration-300 hover:border-primary/30"
                            style={{ 
                              background: 'rgba(31, 39, 49, 0.4)', 
                              borderColor: 'rgba(255, 255, 255, 0.05)' 
                            }}
                          >
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
                                  variant={pairing.active ? "default" : "secondary"}
                                  className="text-xs"
                                >
                                  {pairing.active ? "Active" : "Ended"}
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
                      })}
                    </CardContent>
                  </Card>
                </motion.div>
              )}
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
        </AnimatePresence>
      </main>
    </div>
  )
}
