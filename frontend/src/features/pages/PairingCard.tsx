"use client"

import type React from "react"
import { memo, useCallback, useMemo } from "react"
import { motion } from "framer-motion"
import { Heart, Star, ChevronRight, X, AlertCircle, UserCheck, MessageSquare, Mic, Flame, Settings } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Badge } from "@/components/ui/valorant/badge"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"
import "@/assets/PairingCard.css"

interface PairingCardProps {
  pairing: PairingDTO
  index: number
  user1Profile?: UserProfileDTO
  user2Profile?: UserProfileDTO
  breakupInitiatorProfile?: UserProfileDTO
  isActive: boolean
  onUserClick: (userId: string, event: React.MouseEvent) => void
  onUnpair?: (pairingId: number, event: React.MouseEvent) => void
  onDelete?: (pairingId: number, event: React.MouseEvent) => void
  onManagePair?: (pairing: PairingDTO, event: React.MouseEvent) => void
  formatDate: (dateString: string) => string
  hasAdminActions?: boolean
  // XP System data (optional)
  currentStreak?: number
  currentLevel?: number
}

interface PairingCardListProps {
  pairings: PairingDTO[]
  userProfiles: Record<string, UserProfileDTO>
  isActive: boolean
  onUserClick: (userId: string, event: React.MouseEvent) => void
  onUnpair?: (pairingId: number, event: React.MouseEvent) => void
  onDelete?: (pairingId: number, event: React.MouseEvent) => void
  onManagePair?: (pairing: PairingDTO, event: React.MouseEvent) => void
  formatDate: (dateString: string) => string
  hasAdminActions?: boolean
  maxItems?: number
  emptyMessage?: string
  emptyIcon?: React.ReactNode
  // XP System data (optional) - maps pairing ID to data
  streakData?: Record<number, number>
  levelData?: Record<number, number>
}

export const PairingCard = memo(
  ({
    pairing,
    index,
    user1Profile,
    user2Profile,
    breakupInitiatorProfile,
    isActive,
    onUserClick,
    onUnpair,
    onDelete,
    onManagePair,
    formatDate,
    hasAdminActions = false,
    currentStreak,
    currentLevel,
  }: PairingCardProps) => {
    // Memoize user click handlers to prevent unnecessary re-renders
    const handleUser1Click = useCallback(
      (event: React.MouseEvent) => onUserClick(pairing.user1Id, event),
      [onUserClick, pairing.user1Id],
    )

    const handleUser2Click = useCallback(
      (event: React.MouseEvent) => onUserClick(pairing.user2Id, event),
      [onUserClick, pairing.user2Id],
    )

    const handleUnpair = useCallback((event: React.MouseEvent) => onUnpair?.(pairing.id, event), [onUnpair, pairing.id])

    const handleDelete = useCallback((event: React.MouseEvent) => onDelete?.(pairing.id, event), [onDelete, pairing.id])

    const handleManagePair = useCallback(
      (event: React.MouseEvent) => onManagePair?.(pairing, event),
      [onManagePair, pairing],
    )

    // Memoize breakup information to avoid recalculation - match current implementation exactly
    const breakupInfo = useMemo(() => {
      if (!pairing.breakupReason) return null

      const isAdminBreakup = pairing.breakupInitiatorId?.startsWith("ADMIN_")
      const adminName = isAdminBreakup ? pairing.breakupInitiatorId?.replace("ADMIN_", "") : null

      return {
        isAdminBreakup,
        adminName,
        breakupInitiatorProfile,
        reason:
          pairing.breakupReason.length > 80 ? `${pairing.breakupReason.substring(0, 80)}...` : pairing.breakupReason,
      }
    }, [pairing.breakupReason, pairing.breakupInitiatorId, breakupInitiatorProfile])

    return (
      <div className="pairing-card-wrapper">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: index * 0.1, duration: 0.5, ease: "easeOut" }}
          className={`pairing-card ${isActive ? "pairing-card--active" : "pairing-card--inactive"}`}
        >
          {/* Header Section */}
          <div className="pairing-card__header">
            <div className="pairing-card__status">
              <div
                className={`pairing-card__status-dot ${isActive ? "pairing-card__status-dot--active" : "pairing-card__status-dot--inactive"}`}
              />
              <span className="pairing-card__status-text">{isActive ? "Active" : "Ended"}</span>
            </div>

            {/* Admin Actions */}
            {hasAdminActions && (
              <div className="pairing-card__actions">
                {onManagePair && (
                  <motion.button
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    onClick={handleManagePair}
                    className="pairing-card__action-btn pairing-card__action-btn--manage"
                    title="Manage pair metrics and achievements"
                    aria-label="Manage pair"
                  >
                    <Settings size={14} />
                  </motion.button>
                )}

                {isActive && onUnpair && (
                  <motion.button
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    onClick={handleUnpair}
                    className="pairing-card__action-btn pairing-card__action-btn--unpair"
                    title="Unpair these users (keeps blacklist)"
                    aria-label="Unpair users"
                  >
                    <X size={14} />
                  </motion.button>
                )}

                {!isActive && onDelete && (
                  <motion.button
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    onClick={handleDelete}
                    className="pairing-card__action-btn pairing-card__action-btn--delete"
                    title="Permanently delete this pairing record"
                    aria-label="Delete pairing"
                  >
                    <X size={14} />
                  </motion.button>
                )}
              </div>
            )}
          </div>

          {/* Main Content */}
          <div className="pairing-card__main">
            {/* Users Section */}
            <div className="pairing-card__users">
              {/* User 1 */}
              <motion.div
                className="pairing-card__user"
                onClick={handleUser1Click}
                whileHover={{ y: -1 }}
                whileTap={{ scale: 0.98 }}
                role="button"
                tabIndex={0}
                aria-label={`View profile of ${user1Profile?.displayName || "user"}`}
              >
                <Avatar className="pairing-card__avatar">
                  <AvatarImage src={user1Profile?.avatar || "/placeholder.svg"} />
                  <AvatarFallback className="pairing-card__avatar-fallback">
                    {user1Profile?.displayName?.[0] || user1Profile?.username?.[0] || "?"}
                  </AvatarFallback>
                </Avatar>
                <span className="pairing-card__username">
                  {user1Profile?.displayName || user1Profile?.username || "Unknown"}
                </span>
              </motion.div>

              {/* Connection */}
              <div className="pairing-card__connection">
                <Heart className="pairing-card__heart" size={16} />
              </div>

              {/* User 2 */}
              <motion.div
                className="pairing-card__user"
                onClick={handleUser2Click}
                whileHover={{ y: -1 }}
                whileTap={{ scale: 0.98 }}
                role="button"
                tabIndex={0}
                aria-label={`View profile of ${user2Profile?.displayName || "user"}`}
              >
                <Avatar className="pairing-card__avatar">
                  <AvatarImage src={user2Profile?.avatar || "/placeholder.svg"} />
                  <AvatarFallback className="pairing-card__avatar-fallback">
                    {user2Profile?.displayName?.[0] || user2Profile?.username?.[0] || "?"}
                  </AvatarFallback>
                </Avatar>
                <span className="pairing-card__username">
                  {user2Profile?.displayName || user2Profile?.username || "Unknown"}
                </span>
              </motion.div>
            </div>

            {/* Breakup Information for Inactive Pairings */}
            {breakupInfo && (
              <motion.div
                className="pairing-card__breakup"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                transition={{ duration: 0.3 }}
              >
                <div className="pairing-card__breakup-content">
                  <AlertCircle size={14} className="pairing-card__breakup-icon" />
                  <div className="pairing-card__breakup-info">
                    <span className="pairing-card__breakup-initiator">
                      {breakupInfo.isAdminBreakup ? (
                        <span className="pairing-card__admin-breakup">Admin: {breakupInfo.adminName}</span>
                      ) : breakupInfo.breakupInitiatorProfile ? (
                        <span>
                          {breakupInfo.breakupInitiatorProfile.displayName ||
                            breakupInfo.breakupInitiatorProfile.username}
                        </span>
                      ) : (
                        <span className="pairing-card__breakup-unknown">Unknown</span>
                      )}
                    </span>
                    {pairing.breakupTimestamp && (
                      <span className="pairing-card__breakup-date">{formatDate(pairing.breakupTimestamp)}</span>
                    )}
                  </div>
                </div>
                <p className="pairing-card__breakup-reason">{breakupInfo.reason}</p>
              </motion.div>
            )}

            {/* Engagement Metrics for Active Pairings */}
            {isActive && (
              <motion.div
                className="pairing-card__metrics"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.2, duration: 0.4 }}
              >
                <div className="pairing-card__metric">
                  <Star size={14} className="pairing-card__metric-icon pairing-card__metric-icon--level" />
                  <span className="pairing-card__metric-value">{currentLevel ?? 1}</span>
                </div>

                <div className="pairing-card__metric">
                  <MessageSquare size={14} className="pairing-card__metric-icon pairing-card__metric-icon--messages" />
                  <span className="pairing-card__metric-value">{pairing.messageCount}</span>
                </div>

                <div className="pairing-card__metric">
                  <Mic size={14} className="pairing-card__metric-icon pairing-card__metric-icon--voice" />
                  <span className="pairing-card__metric-value">
                    {Math.floor(pairing.voiceTimeMinutes / 60)}h{pairing.voiceTimeMinutes % 60}m
                  </span>
                </div>

                {currentStreak !== undefined && currentStreak > 0 && (
                  <div className="pairing-card__metric">
                    <Flame size={14} className="pairing-card__metric-icon pairing-card__metric-icon--streak" />
                    <span className="pairing-card__metric-value">{currentStreak}</span>
                  </div>
                )}
              </motion.div>
            )}
          </div>

          {/* Footer */}
          <div className="pairing-card__footer">
            <div className="pairing-card__info">
              {pairing.mutualBreakup && (
                <Badge variant="outline" className="pairing-card__mutual-badge">
                  Mutual
                </Badge>
              )}
              <span className="pairing-card__duration">
                {pairing.activeDays}d {isActive ? "active" : "total"}
              </span>
            </div>
            <ChevronRight size={14} className="pairing-card__chevron" />
          </div>
        </motion.div>
      </div>
    )
  },
)

PairingCard.displayName = "PairingCard"

// List component that handles pagination internally
export const PairingCardList = memo(
  ({
    pairings,
    userProfiles,
    isActive,
    onUserClick,
    onUnpair,
    onDelete,
    onManagePair,
    formatDate,
    hasAdminActions = false,
    maxItems = 5,
    emptyMessage = "No matches found",
    emptyIcon,
    streakData,
    levelData,
  }: PairingCardListProps) => {
    const displayedPairings = useMemo(() => pairings.slice(0, maxItems), [pairings, maxItems])

    if (displayedPairings.length === 0) {
      return (
        <div className="pairing-card-wrapper">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="pairing-card__empty">
            {emptyIcon || (
              <div className="pairing-card__empty-icon">
                <UserCheck size={32} />
              </div>
            )}
            <h3 className="pairing-card__empty-title">{emptyMessage}</h3>
          </motion.div>
        </div>
      )
    }

    return (
      <div className="pairing-card-wrapper">
        <div className="pairing-card__list">
          {displayedPairings.map((pairing, index) => {
            const user1Profile = userProfiles[pairing.user1Id]
            const user2Profile = userProfiles[pairing.user2Id]
            const breakupInitiatorProfile = pairing.breakupInitiatorId
              ? userProfiles[pairing.breakupInitiatorId]
              : undefined

            return (
              <PairingCard
                key={pairing.id}
                pairing={pairing}
                index={index}
                user1Profile={user1Profile}
                user2Profile={user2Profile}
                breakupInitiatorProfile={breakupInitiatorProfile}
                isActive={isActive}
                onUserClick={onUserClick}
                onUnpair={onUnpair}
                onDelete={onDelete}
                onManagePair={onManagePair}
                formatDate={formatDate}
                hasAdminActions={hasAdminActions}
                currentStreak={streakData?.[pairing.id]}
                currentLevel={levelData?.[pairing.id]}
              />
            )
          })}
        </div>
      </div>
    )
  },
)

PairingCardList.displayName = "PairingCardList"
