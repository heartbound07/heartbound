"use client"

import { useState, useEffect, useCallback, useMemo } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import "@/assets/AdminPairManagementModal.css"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/profile/input"
import { Label } from "@/components/ui/valorant/label"
import { Badge } from "@/components/ui/valorant/badge"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Skeleton } from "@/components/ui/SkeletonUI"
import { 
  X, 
  Settings, 
  Trophy, 
  Zap, 
  Activity, 
  MessageSquare, 
  Clock, 
  Star,
  RefreshCw,
  Save,
  RotateCcw,
  User,
  Award,
  TrendingUp,
  Calendar,
  Users,
  Plus,
  Minus,
  Edit3,
  Trash2,
  Lock,
  Unlock
} from 'lucide-react'
import type { 
  PairingDTO, 
  PairLevelDTO, 
  PairAchievementDTO, 
  AchievementDTO, 
  VoiceStreakDTO,
  UpdatePairingActivityDTO,
  UpdatePairLevelDTO,
  ManageAchievementDTO,
  UpdateVoiceStreakDTO,
  CreateVoiceStreakDTO
} from "@/config/pairingService"
import { 
  getPairLevel, 
  getPairingAchievements, 
  getAvailableAchievements, 
  getVoiceStreaks, 
  checkAchievements, 
  updatePairingActivity,
  updatePairLevel,
  manageAchievement,
  updateVoiceStreak,
  createVoiceStreak,
  deleteVoiceStreak
} from "@/config/pairingService"
import { invalidateXPData } from "@/hooks/useXPData"
import type { UserProfileDTO } from "@/config/userService"

interface AdminPairManagementModalProps {
  isOpen: boolean
  onClose: () => void
  pairing: PairingDTO
  userProfiles: Record<string, UserProfileDTO>
  onPairingUpdated?: () => void
}

interface EditableMetrics {
  messageCount: number
  user1MessageCount: number
  user2MessageCount: number
  voiceTimeMinutes: number
  wordCount: number
  emojiCount: number
  activeDays: number
}

interface EditableLevelData {
  currentLevel: number
  totalXP: number
  xpIncrement: number
}

interface StreakStatistics {
  currentStreak: number
  longestStreak: number
  totalStreakDays: number
  averageVoiceMinutes: number
  lastActiveDate: string
}

export function AdminPairManagementModal({ 
  isOpen, 
  onClose, 
  pairing, 
  userProfiles,
  onPairingUpdated
}: AdminPairManagementModalProps) {
  // State management
  const [activeTab, setActiveTab] = useState<'overview' | 'achievements' | 'metrics' | 'streaks'>('overview')
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Data state
  const [pairLevel, setPairLevel] = useState<PairLevelDTO | null>(null)
  const [achievements, setAchievements] = useState<PairAchievementDTO[]>([])
  const [availableAchievements, setAvailableAchievements] = useState<AchievementDTO[]>([])
  const [voiceStreaks, setVoiceStreaks] = useState<VoiceStreakDTO[]>([])
  const [streakStats, setStreakStats] = useState<StreakStatistics | null>(null)

  // Editable metrics state
  const [editableMetrics, setEditableMetrics] = useState<EditableMetrics>({
    messageCount: 0,
    user1MessageCount: 0,
    user2MessageCount: 0,
    voiceTimeMinutes: 0,
    wordCount: 0,
    emojiCount: 0,
    activeDays: 0
  })
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)

  // Admin level/XP controls
  const [editableLevelData, setEditableLevelData] = useState<EditableLevelData>({
    currentLevel: 1,
    totalXP: 0,
    xpIncrement: 0
  })
  const [hasUnsavedLevelChanges, setHasUnsavedLevelChanges] = useState(false)

  // Admin achievement controls
  const [customXPAmount, setCustomXPAmount] = useState<number>(0)

  // Admin streak controls
  const [editingStreakId, setEditingStreakId] = useState<number | null>(null)
  const [showCreateStreak, setShowCreateStreak] = useState(false)

  // Initialize editable metrics from pairing data
  useEffect(() => {
    if (pairing) {
      const user1Messages = pairing.user1MessageCount || 0
      const user2Messages = pairing.user2MessageCount || 0
      const calculatedTotal = user1Messages + user2Messages
      
      setEditableMetrics({
        messageCount: calculatedTotal, // Use calculated total instead of stored value
        user1MessageCount: user1Messages,
        user2MessageCount: user2Messages,
        voiceTimeMinutes: pairing.voiceTimeMinutes,
        wordCount: pairing.wordCount,
        emojiCount: pairing.emojiCount,
        activeDays: pairing.activeDays
      })
    }
  }, [pairing])

  // Initialize level data from loaded data
  useEffect(() => {
    if (pairLevel) {
      setEditableLevelData({
        currentLevel: pairLevel.currentLevel,
        totalXP: pairLevel.totalXP,
        xpIncrement: 0
      })
    }
  }, [pairLevel])

  // Load all data when modal opens
  useEffect(() => {
    if (isOpen && pairing) {
      loadAllData()
    }
  }, [isOpen, pairing])

  const loadAllData = useCallback(async () => {
    setLoading(true)
    setError(null)
    
    try {
      const [levelData, achievementsData, availableData, streaksData] = await Promise.all([
        getPairLevel(pairing.id),
        getPairingAchievements(pairing.id),
        getAvailableAchievements(pairing.id),
        getVoiceStreaks(pairing.id)
      ])

      setPairLevel(levelData)
      setAchievements(achievementsData)
      setAvailableAchievements(availableData)
      setVoiceStreaks(streaksData.recentStreaks)
      setStreakStats(streaksData.statistics)
    } catch (err: any) {
      setError(err.message || 'Failed to load pair data')
    } finally {
      setLoading(false)
    }
  }, [pairing.id])

  const handleMetricChange = useCallback((field: keyof EditableMetrics, value: number) => {
    // Ensure value is a valid number, default to 0 if NaN
    const safeValue = isNaN(value) ? 0 : Math.max(0, value)
    
    setEditableMetrics(prev => {
      const updated = {
        ...prev,
        [field]: safeValue
      }
      
      // Auto-calculate total messages when individual user messages change
      if (field === 'user1MessageCount' || field === 'user2MessageCount') {
        updated.messageCount = updated.user1MessageCount + updated.user2MessageCount
      }
      
      return updated
    })
    setHasUnsavedChanges(true)
  }, [])

  const handleSaveMetrics = useCallback(async () => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      // For admin updates, send direct values instead of increments
      const activity: UpdatePairingActivityDTO = {
        messageIncrement: 0, // Not used for admin updates
        wordIncrement: editableMetrics.wordCount - pairing.wordCount,
        emojiIncrement: editableMetrics.emojiCount - pairing.emojiCount,
        activeDays: editableMetrics.activeDays,
        // Admin direct updates
        user1MessageCount: editableMetrics.user1MessageCount,
        user2MessageCount: editableMetrics.user2MessageCount,
        voiceTimeMinutes: editableMetrics.voiceTimeMinutes
      }

      await updatePairingActivity(pairing.id, activity)
      setSuccessMessage('Metrics updated successfully! XP and achievements will be recalculated automatically.')
      setHasUnsavedChanges(false)
      
      // Invalidate XP cache to ensure fresh data
      invalidateXPData([pairing.id])
      
      // Notify parent component to refresh its data
      if (onPairingUpdated) {
        onPairingUpdated()
      }
      
      // Reload data to show updated values
      setTimeout(() => {
        loadAllData()
      }, 1000)
    } catch (err: any) {
      setError(err.message || 'Failed to update metrics')
    } finally {
      setActionLoading(false)
    }
  }, [editableMetrics, pairing, loadAllData])

  const handleCheckAchievements = useCallback(async () => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const newAchievements = await checkAchievements(pairing.id)
      if (newAchievements.length > 0) {
        setSuccessMessage(`${newAchievements.length} new achievement(s) unlocked!`)
      } else {
        setSuccessMessage('No new achievements available at this time.')
      }
      
      // Reload achievements data
      loadAllData()
    } catch (err: any) {
      setError(err.message || 'Failed to check achievements')
    } finally {
      setActionLoading(false)
    }
  }, [pairing.id, loadAllData])

  const handleResetChanges = useCallback(() => {
    const user1Messages = pairing.user1MessageCount || 0
    const user2Messages = pairing.user2MessageCount || 0
    const calculatedTotal = user1Messages + user2Messages
    
    setEditableMetrics({
      messageCount: calculatedTotal, // Use calculated total instead of stored value
      user1MessageCount: user1Messages,
      user2MessageCount: user2Messages,
      voiceTimeMinutes: pairing.voiceTimeMinutes,
      wordCount: pairing.wordCount,
      emojiCount: pairing.emojiCount,
      activeDays: pairing.activeDays
    })
    setHasUnsavedChanges(false)
  }, [pairing])

  // Admin Level/XP handlers
  const handleLevelDataChange = useCallback((field: keyof EditableLevelData, value: number) => {
    // Ensure value is a valid number, default to 0 if NaN
    const safeValue = isNaN(value) ? 0 : Math.max(0, value)
    setEditableLevelData(prev => ({
      ...prev,
      [field]: safeValue
    }))
    setHasUnsavedLevelChanges(true)
  }, [])

  const handleSaveLevelData = useCallback(async () => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const levelUpdate: UpdatePairLevelDTO = {}
      
      if (editableLevelData.currentLevel !== pairLevel?.currentLevel) {
        levelUpdate.currentLevel = editableLevelData.currentLevel
      }
      if (editableLevelData.totalXP !== pairLevel?.totalXP) {
        levelUpdate.totalXP = editableLevelData.totalXP
      }
      if (editableLevelData.xpIncrement !== 0) {
        levelUpdate.xpIncrement = editableLevelData.xpIncrement
      }

      if (Object.keys(levelUpdate).length > 0) {
        await updatePairLevel(pairing.id, levelUpdate)
        setSuccessMessage('Level and XP updated successfully!')
        setHasUnsavedLevelChanges(false)
        
        // Reload data to show updated values
        setTimeout(() => {
          loadAllData()
        }, 1000)
      } else {
        setSuccessMessage('No changes to save.')
      }
    } catch (err: any) {
      setError(err.message || 'Failed to update level data')
    } finally {
      setActionLoading(false)
    }
  }, [editableLevelData, pairLevel, pairing.id, loadAllData])

  const handleResetLevelChanges = useCallback(() => {
    if (pairLevel) {
      setEditableLevelData({
        currentLevel: pairLevel.currentLevel,
        totalXP: pairLevel.totalXP,
        xpIncrement: 0
      })
      setHasUnsavedLevelChanges(false)
    }
  }, [pairLevel])

  // Admin Achievement handlers
  const handleManageAchievement = useCallback(async (achievementId: number, action: 'unlock' | 'lock') => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const achievementAction: ManageAchievementDTO = {
        achievementId,
        action,
        customXP: customXPAmount > 0 ? customXPAmount : undefined
      }

      await manageAchievement(pairing.id, achievementAction)
      setSuccessMessage(`Achievement ${action}ed successfully!`)
      setCustomXPAmount(0)
      
      // Reload data to show updated achievements
      loadAllData()
    } catch (err: any) {
      setError(err.message || `Failed to ${action} achievement`)
    } finally {
      setActionLoading(false)
    }
  }, [pairing.id, customXPAmount, loadAllData])

  // Admin Voice Streak handlers
  const handleUpdateVoiceStreak = useCallback(async (streakId: number, streakData: UpdateVoiceStreakDTO) => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      await updateVoiceStreak(streakId, streakData)
      setSuccessMessage('Voice streak updated successfully!')
      setEditingStreakId(null)
      
      // Reload data to show updated streaks
      loadAllData()
    } catch (err: any) {
      setError(err.message || 'Failed to update voice streak')
    } finally {
      setActionLoading(false)
    }
  }, [loadAllData])

  const handleCreateVoiceStreak = useCallback(async (streakData: CreateVoiceStreakDTO) => {
    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      await createVoiceStreak(pairing.id, streakData)
      setSuccessMessage('Voice streak created successfully!')
      setShowCreateStreak(false)
      
      // Reload data to show new streak
      loadAllData()
    } catch (err: any) {
      setError(err.message || 'Failed to create voice streak')
    } finally {
      setActionLoading(false)
    }
  }, [pairing.id, loadAllData])

  const handleDeleteVoiceStreak = useCallback(async (streakId: number) => {
    if (!confirm('Are you sure you want to delete this voice streak? This action cannot be undone.')) {
      return
    }

    setActionLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      await deleteVoiceStreak(streakId)
      setSuccessMessage('Voice streak deleted successfully!')
      
      // Reload data to show updated streaks
      loadAllData()
    } catch (err: any) {
      setError(err.message || 'Failed to delete voice streak')
    } finally {
      setActionLoading(false)
    }
  }, [loadAllData])

  const handleClose = useCallback(() => {
    if (hasUnsavedChanges || hasUnsavedLevelChanges) {
      if (confirm('You have unsaved changes. Are you sure you want to close?')) {
        onClose()
      }
    } else {
      onClose()
    }
  }, [hasUnsavedChanges, hasUnsavedLevelChanges, onClose])

  // Create Streak Form Component
  const CreateStreakForm = ({ onSubmit, onCancel, isLoading }: {
    onSubmit: (data: CreateVoiceStreakDTO) => void
    onCancel: () => void
    isLoading: boolean
  }) => {
    const [formData, setFormData] = useState<CreateVoiceStreakDTO>({
      streakDate: new Date().toISOString().split('T')[0],
      voiceMinutes: 0,
      streakCount: 1,
      active: true
    })

    return (
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <Label htmlFor="streakDate" className="text-white mb-2 block text-sm">Date</Label>
          <Input
            id="streakDate"
            type="date"
            value={formData.streakDate}
            onChange={(e) => setFormData(prev => ({ ...prev, streakDate: e.target.value }))}
            className="bg-theme-container border-theme text-white"
          />
        </div>
        <div>
          <Label htmlFor="voiceMinutes" className="text-white mb-2 block text-sm">Voice Minutes</Label>
          <Input
            id="voiceMinutes"
            type="number"
            value={formData.voiceMinutes}
            onChange={(e) => setFormData(prev => ({ ...prev, voiceMinutes: parseInt(e.target.value) || 0 }))}
            className="bg-theme-container border-theme text-white"
            min="0"
          />
        </div>
        <div>
          <Label htmlFor="streakCount" className="text-white mb-2 block text-sm">Streak Count</Label>
          <Input
            id="streakCount"
            type="number"
            value={formData.streakCount}
            onChange={(e) => setFormData(prev => ({ ...prev, streakCount: parseInt(e.target.value) || 1 }))}
            className="bg-theme-container border-theme text-white"
            min="1"
          />
        </div>
        <div className="flex items-center space-x-2">
          <input
            id="active"
            type="checkbox"
            checked={formData.active}
            onChange={(e) => setFormData(prev => ({ ...prev, active: e.target.checked }))}
            className="rounded border-theme"
          />
          <Label htmlFor="active" className="text-white text-sm">Active Streak</Label>
        </div>
        <div className="md:col-span-2 flex gap-3 pt-4">
          <Button
            onClick={() => onSubmit(formData)}
            disabled={isLoading}
            className="valorant-button-success"
          >
            {isLoading ? <RefreshCw className="h-4 w-4 mr-2 animate-spin" /> : <Plus className="h-4 w-4 mr-2" />}
            Create Streak
          </Button>
          <Button
            onClick={onCancel}
            variant="outline"
            className="border-theme text-theme-secondary hover:border-status-error/50 hover:text-status-error"
          >
            Cancel
          </Button>
        </div>
      </div>
    )
  }

  // Edit Streak Form Component
  const EditStreakForm = ({ streak, onSubmit, onCancel, isLoading }: {
    streak: VoiceStreakDTO
    onSubmit: (data: UpdateVoiceStreakDTO) => void  
    onCancel: () => void
    isLoading: boolean
  }) => {
    const [formData, setFormData] = useState<UpdateVoiceStreakDTO>({
      streakDate: streak.streakDate,
      voiceMinutes: streak.voiceMinutes,
      streakCount: streak.streakCount,
      active: streak.active
    })

    return (
      <div className="p-4 rounded-lg bg-status-info/10 border border-status-info/20">
        <h5 className="text-white font-semibold mb-3">Edit Streak - {streak.streakDate}</h5>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <Label htmlFor="editStreakDate" className="text-white mb-2 block text-sm">Date</Label>
            <Input
              id="editStreakDate"
              type="date"
              value={formData.streakDate}
              onChange={(e) => setFormData(prev => ({ ...prev, streakDate: e.target.value }))}
              className="bg-theme-container border-theme text-white"
            />
          </div>
          <div>
            <Label htmlFor="editVoiceMinutes" className="text-white mb-2 block text-sm">Voice Minutes</Label>
            <Input
              id="editVoiceMinutes"
              type="number"
              value={formData.voiceMinutes}
              onChange={(e) => setFormData(prev => ({ ...prev, voiceMinutes: parseInt(e.target.value) || 0 }))}
              className="bg-theme-container border-theme text-white"
              min="0"
            />
          </div>
          <div>
            <Label htmlFor="editStreakCount" className="text-white mb-2 block text-sm">Streak Count</Label>
            <Input
              id="editStreakCount"
              type="number"
              value={formData.streakCount}
              onChange={(e) => setFormData(prev => ({ ...prev, streakCount: parseInt(e.target.value) || 1 }))}
              className="bg-theme-container border-theme text-white"
              min="1"
            />
          </div>
          <div className="flex items-center space-x-2">
            <input
              id="editActive"
              type="checkbox"
              checked={formData.active}
              onChange={(e) => setFormData(prev => ({ ...prev, active: e.target.checked }))}
              className="rounded border-theme"
            />
            <Label htmlFor="editActive" className="text-white text-sm">Active Streak</Label>
          </div>
          <div className="md:col-span-2 flex gap-3 pt-2">
            <Button
              onClick={() => onSubmit(formData)}
              disabled={isLoading}
              className="valorant-button-success"
              size="sm"
            >
              {isLoading ? <RefreshCw className="h-3 w-3 mr-1 animate-spin" /> : <Save className="h-3 w-3 mr-1" />}
              Update
            </Button>
            <Button
              onClick={onCancel}
              variant="outline"
              size="sm"
              className="border-theme text-theme-secondary hover:border-status-error/50 hover:text-status-error"
            >
              Cancel
            </Button>
          </div>
        </div>
      </div>
    )
  }

  // Memoize user profiles to prevent unnecessary recalculations
  const user1Profile = useMemo(() => userProfiles[pairing.user1Id], [userProfiles, pairing.user1Id])
  const user2Profile = useMemo(() => userProfiles[pairing.user2Id], [userProfiles, pairing.user2Id])

  // Memoize tab configuration to prevent unnecessary re-renders
  const tabConfig = useMemo(() => [
    { id: 'overview', label: 'Overview', icon: Activity },
    { id: 'achievements', label: 'Achievements', icon: Trophy },
    { id: 'metrics', label: 'Metrics', icon: TrendingUp },
    { id: 'streaks', label: 'Voice Streaks', icon: Calendar },
  ], [])

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
      <motion.div
        initial={{ scale: 0.95, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.95, opacity: 0 }}
        className="relative w-full max-w-6xl max-h-[90vh] overflow-hidden"
      >
        <Card className="admin-pair-management-modal-container h-full flex flex-col">
          {/* Header */}
          <CardHeader className="pb-4 border-b border-theme flex-shrink-0">
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-3 text-white">
                <div className="p-2 bg-primary/20 rounded-lg">
                  <Settings className="h-6 w-6 text-primary" />
                </div>
                Pair Management
                <Badge variant="outline" className="text-xs">
                  ID: {pairing.id}
                </Badge>
              </CardTitle>
              <Button
                variant="outline"
                size="sm"
                onClick={handleClose}
                className="border-theme-tertiary/30 text-theme-secondary hover:border-status-error/50 hover:text-status-error"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>

            {/* User Profiles */}
            <div className="flex items-center gap-6 mt-4">
              <div className="flex items-center gap-3">
                <Avatar className="h-12 w-12">
                  <AvatarImage src={user1Profile?.avatar} alt={user1Profile?.displayName} />
                  <AvatarFallback className="bg-primary/20 text-primary">
                    {user1Profile?.displayName?.charAt(0) || "U1"}
                  </AvatarFallback>
                </Avatar>
                <div>
                  <p className="font-semibold text-white">{user1Profile?.displayName || "User 1"}</p>
                  <p className="text-sm text-theme-secondary">{pairing.user1Age} • {pairing.user1Rank}</p>
                </div>
              </div>
              
              <div className="flex items-center gap-2">
                <div className="w-8 h-0.5 bg-primary"></div>
                <Users className="h-5 w-5 text-primary" />
                <div className="w-8 h-0.5 bg-primary"></div>
              </div>

              <div className="flex items-center gap-3">
                <Avatar className="h-12 w-12">
                  <AvatarImage src={user2Profile?.avatar} alt={user2Profile?.displayName} />
                  <AvatarFallback className="bg-primary/20 text-primary">
                    {user2Profile?.displayName?.charAt(0) || "U2"}
                  </AvatarFallback>
                </Avatar>
                <div>
                  <p className="font-semibold text-white">{user2Profile?.displayName || "User 2"}</p>
                  <p className="text-sm text-theme-secondary">{pairing.user2Age} • {pairing.user2Rank}</p>
                </div>
              </div>
            </div>

            {/* Tab Navigation */}
            <div className="flex gap-2 mt-4">
              {tabConfig.map(({ id, label, icon: Icon }) => (
                <Button
                  key={id}
                  variant={activeTab === id ? "default" : "outline"}
                  size="sm"
                  onClick={() => setActiveTab(id as any)}
                  className={`${
                    activeTab === id 
                      ? "bg-primary text-white" 
                      : "border-theme text-theme-secondary hover:border-primary/50 hover:text-primary"
                  }`}
                >
                  <Icon className="h-4 w-4 mr-2" />
                  {label}
                </Button>
              ))}
            </div>
          </CardHeader>

          {/* Content */}
          <CardContent className="flex-1 overflow-y-auto p-6">
            {/* Error/Success Messages */}
            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="mb-4 p-4 rounded-xl bg-status-error/10 border border-status-error/20 text-status-error"
                >
                  {error}
                </motion.div>
              )}
              {successMessage && (
                <motion.div
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="mb-4 p-4 rounded-xl bg-status-success/10 border border-status-success/20 text-status-success"
                >
                  {successMessage}
                </motion.div>
              )}
            </AnimatePresence>

            {loading ? (
              <div className="space-y-6">
                {[1, 2, 3].map(i => (
                  <div key={i} className="p-4 rounded-xl bg-theme-container border-theme">
                    <Skeleton width="200px" height="20px" className="mb-3" theme="valorant" />
                    <div className="grid grid-cols-2 gap-4">
                      <Skeleton width="100%" height="60px" theme="valorant" />
                      <Skeleton width="100%" height="60px" theme="valorant" />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <>
                {/* Overview Tab */}
                {activeTab === 'overview' && (
                  <div className="space-y-6">
                    {/* XP & Level Summary with Admin Controls */}
                    {pairLevel && (
                      <div className="p-6 rounded-xl bg-theme-container border-theme">
                        <div className="flex items-center justify-between mb-4">
                          <h3 className="text-lg font-semibold text-white flex items-center gap-2">
                            <Star className="h-5 w-5 text-primary" />
                            Pair Level & XP
                          </h3>
                          
                          {/* Admin Level Controls */}
                          <div className="flex items-center gap-2">
                            <Button
                              onClick={handleSaveLevelData}
                              disabled={actionLoading || !hasUnsavedLevelChanges}
                              className="valorant-button-success"
                              size="sm"
                            >
                              {actionLoading ? (
                                <RefreshCw className="h-3 w-3 mr-1 animate-spin" />
                              ) : (
                                <Save className="h-3 w-3 mr-1" />
                              )}
                              Save XP
                            </Button>
                            <Button
                              onClick={handleResetLevelChanges}
                              disabled={!hasUnsavedLevelChanges}
                              variant="outline"
                              size="sm"
                              className="border-theme text-theme-secondary hover:border-status-warning/50 hover:text-status-warning"
                            >
                              <RotateCcw className="h-3 w-3 mr-1" />
                              Reset
                            </Button>
                          </div>
                        </div>
                        
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
                          <div className="text-center p-4 rounded-lg bg-primary/10 border border-primary/20">
                            <div className="text-2xl font-bold text-primary mb-1">{pairLevel.currentLevel}</div>
                            <div className="text-sm text-theme-secondary">Current Level</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-status-success/10 border border-status-success/20">
                            <div className="text-2xl font-bold text-status-success mb-1">{pairLevel.totalXP}</div>
                            <div className="text-sm text-theme-secondary">Total XP</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-status-info/10 border border-status-info/20">
                            <div className="text-2xl font-bold text-status-info mb-1">{pairLevel.xpNeededForNextLevel}</div>
                            <div className="text-sm text-theme-secondary">XP to Next Level</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-status-warning/10 border border-status-warning/20">
                            <div className="text-2xl font-bold text-status-warning mb-1">
                              {pairLevel.levelProgressPercentage != null && !isNaN(pairLevel.levelProgressPercentage) 
                                ? Math.round(pairLevel.levelProgressPercentage) 
                                : 0}%
                            </div>
                            <div className="text-sm text-theme-secondary">Progress</div>
                          </div>
                        </div>

                        {/* Admin XP/Level Adjustment Controls */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-4 rounded-lg bg-primary/5 border border-primary/10">
                          <div>
                            <Label htmlFor="adminLevel" className="text-white mb-2 block text-sm">Set Level</Label>
                            <Input
                              id="adminLevel"
                              type="number"
                              value={editableLevelData.currentLevel}
                              onChange={(e) => handleLevelDataChange('currentLevel', parseInt(e.target.value) || 1)}
                              className="bg-theme-container border-theme text-white text-sm"
                              min="1"
                            />
                          </div>
                          <div>
                            <Label htmlFor="adminTotalXP" className="text-white mb-2 block text-sm">Set Total XP</Label>
                            <Input
                              id="adminTotalXP"
                              type="number"
                              value={editableLevelData.totalXP}
                              onChange={(e) => handleLevelDataChange('totalXP', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white text-sm"
                              min="0"
                            />
                          </div>
                          <div>
                            <Label htmlFor="adminXPIncrement" className="text-white mb-2 block text-sm">Add/Remove XP</Label>
                            <div className="flex gap-2">
                              <Input
                                id="adminXPIncrement"
                                type="number"
                                value={editableLevelData.xpIncrement}
                                onChange={(e) => handleLevelDataChange('xpIncrement', parseInt(e.target.value) || 0)}
                                className="bg-theme-container border-theme text-white text-sm flex-1"
                                placeholder="±XP"
                              />
                              <Button
                                onClick={() => handleLevelDataChange('xpIncrement', editableLevelData.xpIncrement + 100)}
                                size="sm"
                                variant="outline"
                                className="border-status-success/30 text-status-success hover:bg-status-success/10"
                              >
                                <Plus className="h-3 w-3" />
                              </Button>
                              <Button
                                onClick={() => handleLevelDataChange('xpIncrement', editableLevelData.xpIncrement - 100)}
                                size="sm"
                                variant="outline"
                                className="border-status-error/30 text-status-error hover:bg-status-error/10"
                              >
                                <Minus className="h-3 w-3" />
                              </Button>
                            </div>
                          </div>
                        </div>

                        {hasUnsavedLevelChanges && (
                          <div className="mt-4 p-3 rounded-lg bg-status-info/10 border border-status-info/20">
                            <p className="text-status-info text-sm">
                              <strong>Preview:</strong> Level will be set to {editableLevelData.currentLevel}, 
                              Total XP to {editableLevelData.totalXP}
                              {editableLevelData.xpIncrement !== 0 && (
                                <>, with ${editableLevelData.xpIncrement > 0 ? '+' : ''}{editableLevelData.xpIncrement} XP adjustment</>
                              )}
                            </p>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Recent Achievements */}
                    <div className="p-6 rounded-xl bg-theme-container border-theme">
                      <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                        <Trophy className="h-5 w-5 text-status-warning" />
                        Recent Achievements
                        <Badge variant="outline" className="text-xs">
                          {achievements.length} Total
                        </Badge>
                      </h3>
                      {achievements.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {achievements.slice(0, 4).map((achievement) => (
                            <div key={achievement.id} className="flex items-center gap-3 p-3 rounded-lg bg-status-warning/10 border border-status-warning/20">
                              <Award className="h-5 w-5 text-status-warning" />
                              <div className="flex-1">
                                <p className="font-medium text-white">{achievement.achievement.name}</p>
                                <p className="text-xs text-theme-secondary">{achievement.unlockTimeDisplay}</p>
                              </div>
                              <Badge variant="outline" className="text-xs">
                                +{achievement.xpAwarded} XP
                              </Badge>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-theme-secondary">No achievements unlocked yet.</p>
                      )}
                    </div>

                    {/* Activity Overview */}
                    <div className="p-6 rounded-xl bg-theme-container border-theme">
                      <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                        <Activity className="h-5 w-5 text-status-info" />
                        Activity Summary
                      </h3>
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div className="text-center p-4 rounded-lg bg-status-success/10 border border-status-success/20">
                          <MessageSquare className="h-6 w-6 text-status-success mx-auto mb-2" />
                          <div className="text-lg font-bold text-status-success mb-1">{pairing.messageCount}</div>
                          <div className="text-sm text-theme-secondary">Total Messages</div>
                        </div>
                        <div className="text-center p-4 rounded-lg bg-primary/10 border border-primary/20">
                          <Clock className="h-6 w-6 text-primary mx-auto mb-2" />
                          <div className="text-lg font-bold text-primary mb-1">
                            {Math.floor(pairing.voiceTimeMinutes / 60)}h {pairing.voiceTimeMinutes % 60}m
                          </div>
                          <div className="text-sm text-theme-secondary">Voice Time</div>
                        </div>
                        <div className="text-center p-4 rounded-lg bg-status-info/10 border border-status-info/20">
                          <User className="h-6 w-6 text-status-info mx-auto mb-2" />
                          <div className="text-lg font-bold text-status-info mb-1">{pairing.wordCount}</div>
                          <div className="text-sm text-theme-secondary">Words</div>
                        </div>
                        <div className="text-center p-4 rounded-lg bg-status-warning/10 border border-status-warning/20">
                          <Calendar className="h-6 w-6 text-status-warning mx-auto mb-2" />
                          <div className="text-lg font-bold text-status-warning mb-1">{pairing.activeDays}</div>
                          <div className="text-sm text-theme-secondary">Active Days</div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Achievements Tab */}
                {activeTab === 'achievements' && (
                  <div className="space-y-6">
                    {/* Admin Actions */}
                    <div className="flex gap-3 flex-wrap">
                      <Button
                        onClick={handleCheckAchievements}
                        disabled={actionLoading}
                        className="valorant-button-primary"
                      >
                        {actionLoading ? (
                          <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                        ) : (
                          <Zap className="h-4 w-4 mr-2" />
                        )}
                        Check Achievements
                      </Button>
                      
                      {/* Custom XP Input for Manual Unlocks */}
                      <div className="flex items-center gap-2">
                        <Label htmlFor="customXP" className="text-white text-sm whitespace-nowrap">Custom XP:</Label>
                        <Input
                          id="customXP"
                          type="number"
                          value={customXPAmount}
                          onChange={(e) => setCustomXPAmount(parseInt(e.target.value) || 0)}
                          className="bg-theme-container border-theme text-white w-24"
                          min="0"
                          placeholder="0"
                        />
                      </div>
                    </div>

                    {/* Unlocked Achievements */}
                    <div className="p-6 rounded-xl bg-theme-container border-theme">
                      <h3 className="text-lg font-semibold text-white mb-4">
                        Unlocked Achievements ({achievements.length})
                      </h3>
                      {achievements.length > 0 ? (
                        <div className="space-y-3">
                          {achievements.map((achievement) => (
                            <div key={achievement.id} className="flex items-center gap-4 p-4 rounded-lg bg-status-success/10 border border-status-success/20">
                              <Trophy className="h-6 w-6 text-status-warning flex-shrink-0" />
                              <div className="flex-1">
                                <div className="flex items-center gap-2 mb-1">
                                  <h4 className="font-semibold text-white">{achievement.achievement.name}</h4>
                                  <Badge variant="outline" className="text-xs">
                                    {achievement.achievement.rarity}
                                  </Badge>
                                </div>
                                <p className="text-sm text-theme-secondary mb-2">{achievement.achievement.description}</p>
                                <div className="flex items-center gap-4 text-xs text-theme-tertiary">
                                  <span>Unlocked: {achievement.unlockTimeDisplay}</span>
                                  <span>XP Awarded: +{achievement.xpAwarded}</span>
                                  <span>Progress: {achievement.progressValue}</span>
                                </div>
                              </div>
                              
                              {/* Admin Lock Button */}
                              <Button
                                onClick={() => handleManageAchievement(achievement.achievement.id, 'lock')}
                                disabled={actionLoading}
                                variant="outline"
                                size="sm"
                                className="border-status-error/30 text-status-error hover:bg-status-error/10"
                                title="Lock this achievement"
                              >
                                <Lock className="h-3 w-3" />
                              </Button>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-theme-secondary">No achievements unlocked yet.</p>
                      )}
                    </div>

                    {/* Available Achievements */}
                    <div className="p-6 rounded-xl bg-theme-container border-theme">
                      <h3 className="text-lg font-semibold text-white mb-4">
                        Available Achievements ({availableAchievements.length})
                      </h3>
                      {availableAchievements.length > 0 ? (
                        <div className="space-y-3">
                          {availableAchievements.map((achievement) => (
                            <div key={achievement.id} className="flex items-center gap-4 p-4 rounded-lg bg-theme-container border border-theme-tertiary/30">
                              <Award className="h-6 w-6 text-theme-secondary flex-shrink-0" />
                              <div className="flex-1">
                                <div className="flex items-center gap-2 mb-1">
                                  <h4 className="font-semibold text-white">{achievement.name}</h4>
                                  <Badge variant="outline" className="text-xs">
                                    {achievement.rarity}
                                  </Badge>
                                </div>
                                <p className="text-sm text-theme-secondary mb-2">{achievement.description}</p>
                                <div className="flex items-center gap-4 text-xs text-theme-tertiary">
                                  <span>Requirement: {achievement.requirementDescription}</span>
                                  <span>XP Reward: +{achievement.xpReward}</span>
                                  {customXPAmount > 0 && (
                                    <span className="text-status-info">Override XP: +{customXPAmount}</span>
                                  )}
                                </div>
                              </div>
                              
                              {/* Admin Unlock Button */}
                              <Button
                                onClick={() => handleManageAchievement(achievement.id, 'unlock')}
                                disabled={actionLoading}
                                variant="outline"
                                size="sm"
                                className="border-status-success/30 text-status-success hover:bg-status-success/10"
                                title={`Unlock this achievement${customXPAmount > 0 ? ` with ${customXPAmount} XP` : ''}`}
                              >
                                <Unlock className="h-3 w-3" />
                              </Button>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-theme-secondary">All achievements have been unlocked!</p>
                      )}
                    </div>
                  </div>
                )}

                {/* Metrics Tab */}
                {activeTab === 'metrics' && (
                  <div className="space-y-6">
                    {/* Save Actions */}
                    <div className="flex items-center gap-3">
                      <Button
                        onClick={handleSaveMetrics}
                        disabled={actionLoading || !hasUnsavedChanges}
                        className="valorant-button-success"
                      >
                        {actionLoading ? (
                          <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                        ) : (
                          <Save className="h-4 w-4 mr-2" />
                        )}
                        Save Changes
                      </Button>
                      <Button
                        onClick={handleResetChanges}
                        disabled={!hasUnsavedChanges}
                        variant="outline"
                        className="border-theme text-theme-secondary hover:border-status-warning/50 hover:text-status-warning"
                      >
                        <RotateCcw className="h-4 w-4 mr-2" />
                        Reset
                      </Button>
                      {hasUnsavedChanges && (
                        <Badge variant="outline" className="text-status-warning border-status-warning/30">
                          Unsaved Changes
                        </Badge>
                      )}
                    </div>

                    {/* Editable Metrics */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      {/* Message Metrics */}
                      <div className="p-6 rounded-xl bg-theme-container border-theme">
                        <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <MessageSquare className="h-5 w-5 text-status-success" />
                          Message Metrics
                        </h3>
                        <div className="space-y-4">
                          <div>
                            <Label htmlFor="totalMessages" className="text-white mb-2 block">Total Messages (Auto-calculated)</Label>
                            <Input
                              id="totalMessages"
                              type="number"
                              value={editableMetrics.messageCount}
                              readOnly
                              className="bg-theme-container/50 border-theme text-white cursor-not-allowed opacity-75"
                              min="0"
                            />
                            <div className="text-xs text-theme-secondary mt-1">
                              Automatically calculated from individual user messages
                            </div>
                          </div>
                          <div>
                            <Label htmlFor="user1Messages" className="text-white mb-2 block">
                              {user1Profile?.displayName || "User 1"} Messages
                            </Label>
                            <Input
                              id="user1Messages"
                              type="number"
                              value={editableMetrics.user1MessageCount}
                              onChange={(e) => handleMetricChange('user1MessageCount', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                          <div>
                            <Label htmlFor="user2Messages" className="text-white mb-2 block">
                              {user2Profile?.displayName || "User 2"} Messages
                            </Label>
                            <Input
                              id="user2Messages"
                              type="number"
                              value={editableMetrics.user2MessageCount}
                              onChange={(e) => handleMetricChange('user2MessageCount', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                        </div>
                      </div>

                      {/* Activity Metrics */}
                      <div className="p-6 rounded-xl bg-theme-container border-theme">
                        <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <Activity className="h-5 w-5 text-primary" />
                          Activity Metrics
                        </h3>
                        <div className="space-y-4">
                          <div>
                            <Label htmlFor="voiceTime" className="text-white mb-2 block">Voice Time (minutes)</Label>
                            <Input
                              id="voiceTime"
                              type="number"
                              value={editableMetrics.voiceTimeMinutes}
                              onChange={(e) => handleMetricChange('voiceTimeMinutes', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                          <div>
                            <Label htmlFor="wordCount" className="text-white mb-2 block">Word Count</Label>
                            <Input
                              id="wordCount"
                              type="number"
                              value={editableMetrics.wordCount}
                              onChange={(e) => handleMetricChange('wordCount', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                          <div>
                            <Label htmlFor="emojiCount" className="text-white mb-2 block">Emoji Count</Label>
                            <Input
                              id="emojiCount"
                              type="number"
                              value={editableMetrics.emojiCount}
                              onChange={(e) => handleMetricChange('emojiCount', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                          <div>
                            <Label htmlFor="activeDays" className="text-white mb-2 block">Active Days</Label>
                            <Input
                              id="activeDays"
                              type="number"
                              value={editableMetrics.activeDays}
                              onChange={(e) => handleMetricChange('activeDays', parseInt(e.target.value) || 0)}
                              className="bg-theme-container border-theme text-white"
                              min="0"
                            />
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Preview Changes */}
                    {hasUnsavedChanges && (
                      <div className="p-6 rounded-xl bg-status-info/10 border border-status-info/20">
                        <h3 className="text-lg font-semibold text-status-info mb-4">Preview Changes</h3>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                          <div>
                            <span className="text-theme-secondary">Messages: </span>
                            <span className="text-white">{pairing.messageCount}</span>
                            <span className="text-status-info"> → {editableMetrics.messageCount}</span>
                          </div>
                          <div>
                            <span className="text-theme-secondary">Voice: </span>
                            <span className="text-white">{pairing.voiceTimeMinutes}m</span>
                            <span className="text-status-info"> → {editableMetrics.voiceTimeMinutes}m</span>
                          </div>
                          <div>
                            <span className="text-theme-secondary">Words: </span>
                            <span className="text-white">{pairing.wordCount}</span>
                            <span className="text-status-info"> → {editableMetrics.wordCount}</span>
                          </div>
                          <div>
                            <span className="text-theme-secondary">Active Days: </span>
                            <span className="text-white">{pairing.activeDays}</span>
                            <span className="text-status-info"> → {editableMetrics.activeDays}</span>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Streaks Tab */}
                {activeTab === 'streaks' && (
                  <div className="space-y-6">
                    {/* Admin Streak Actions */}
                    <div className="flex gap-3 flex-wrap">
                      <Button
                        onClick={() => setShowCreateStreak(true)}
                        disabled={actionLoading}
                        className="valorant-button-primary"
                      >
                        <Plus className="h-4 w-4 mr-2" />
                        Create New Streak
                      </Button>
                    </div>

                    {/* Create New Streak Form */}
                    {showCreateStreak && (
                      <div className="p-6 rounded-xl bg-primary/5 border border-primary/10">
                        <h4 className="text-lg font-semibold text-white mb-4">Create New Voice Streak</h4>
                        <CreateStreakForm 
                          onSubmit={handleCreateVoiceStreak}
                          onCancel={() => setShowCreateStreak(false)}
                          isLoading={actionLoading}
                        />
                      </div>
                    )}

                    {/* Streak Statistics */}
                    {streakStats && (
                      <div className="p-6 rounded-xl bg-theme-container border-theme">
                        <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <Calendar className="h-5 w-5 text-status-warning" />
                          Streak Statistics
                        </h3>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                          <div className="text-center p-4 rounded-lg bg-status-success/10 border border-status-success/20">
                            <div className="text-2xl font-bold text-status-success mb-1">{streakStats.currentStreak || 0}</div>
                            <div className="text-sm text-theme-secondary">Current Streak</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-primary/10 border border-primary/20">
                            <div className="text-2xl font-bold text-primary mb-1">{streakStats.longestStreak || 0}</div>
                            <div className="text-sm text-theme-secondary">Longest Streak</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-status-info/10 border border-status-info/20">
                            <div className="text-2xl font-bold text-status-info mb-1">{streakStats.totalStreakDays || 0}</div>
                            <div className="text-sm text-theme-secondary">Total Streak Days</div>
                          </div>
                          <div className="text-center p-4 rounded-lg bg-status-warning/10 border border-status-warning/20">
                            <div className="text-2xl font-bold text-status-warning mb-1">
                              {streakStats.averageVoiceMinutes != null && !isNaN(streakStats.averageVoiceMinutes) 
                                ? Math.round(streakStats.averageVoiceMinutes) 
                                : 0}
                            </div>
                            <div className="text-sm text-theme-secondary">Avg Voice/Day</div>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Recent Streaks with Admin Controls */}
                    <div className="p-6 rounded-xl bg-theme-container border-theme">
                      <h3 className="text-lg font-semibold text-white mb-4">
                        Voice Streaks Management
                      </h3>
                      {voiceStreaks.length > 0 ? (
                        <div className="space-y-3">
                          {voiceStreaks.map((streak) => (
                            <div key={streak.id}>
                              {editingStreakId === streak.id ? (
                                <EditStreakForm
                                  streak={streak}
                                  onSubmit={(data) => handleUpdateVoiceStreak(streak.id, data)}
                                  onCancel={() => setEditingStreakId(null)}
                                  isLoading={actionLoading}
                                />
                              ) : (
                                <div className="flex items-center gap-4 p-4 rounded-lg bg-theme-container border border-theme-tertiary/30">
                                  <Calendar className="h-5 w-5 text-status-warning flex-shrink-0" />
                                  <div className="flex-1">
                                    <div className="flex items-center gap-2 mb-1">
                                      <span className="font-semibold text-white">{streak.streakDate}</span>
                                      {streak.isToday && <Badge variant="outline" className="text-xs">Today</Badge>}
                                      {streak.isYesterday && <Badge variant="outline" className="text-xs">Yesterday</Badge>}
                                      {streak.active && <Badge variant="outline" className="text-status-success border-status-success/30">Active</Badge>}
                                    </div>
                                    <div className="flex items-center gap-4 text-sm text-theme-secondary">
                                      <span>Voice: {streak.voiceMinutes}m</span>
                                      <span>Streak: {streak.streakCount} days</span>
                                      <span>Tier: {streak.streakTier}</span>
                                      <span>XP: +{streak.streakXPReward}</span>
                                    </div>
                                  </div>
                                  
                                  {/* Admin Buttons */}
                                  <div className="flex gap-2">
                                    <Button
                                      onClick={() => setEditingStreakId(streak.id)}
                                      disabled={actionLoading}
                                      variant="outline"
                                      size="sm"
                                      className="border-status-info/30 text-status-info hover:bg-status-info/10"
                                      title="Edit this streak"
                                    >
                                      <Edit3 className="h-3 w-3" />
                                    </Button>
                                    <Button
                                      onClick={() => handleDeleteVoiceStreak(streak.id)}
                                      disabled={actionLoading}
                                      variant="outline"
                                      size="sm"
                                      className="border-status-error/30 text-status-error hover:bg-status-error/10"
                                      title="Delete this streak"
                                    >
                                      <Trash2 className="h-3 w-3" />
                                    </Button>
                                  </div>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-theme-secondary">No voice streak data available.</p>
                      )}
                    </div>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </motion.div>
    </div>
  )
} 