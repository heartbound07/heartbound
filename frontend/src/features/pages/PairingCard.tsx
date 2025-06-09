import React, { memo, useCallback, useMemo } from 'react'
import { motion } from 'framer-motion'
import { Heart, Star, ChevronRight, X, AlertCircle, UserCheck, MessageSquare, Mic } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/valorant/avatar'
import { Badge } from '@/components/ui/valorant/badge'
import type { PairingDTO } from '@/config/pairingService'
import type { UserProfileDTO } from '@/config/userService'

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
  formatDate: (dateString: string) => string
  hasAdminActions?: boolean
}

interface PairingCardListProps {
  pairings: PairingDTO[]
  userProfiles: Record<string, UserProfileDTO>
  isActive: boolean
  onUserClick: (userId: string, event: React.MouseEvent) => void
  onUnpair?: (pairingId: number, event: React.MouseEvent) => void
  onDelete?: (pairingId: number, event: React.MouseEvent) => void
  formatDate: (dateString: string) => string
  hasAdminActions?: boolean
  maxItems?: number
  emptyMessage?: string
  emptyIcon?: React.ReactNode
}

export const PairingCard = memo(({
  pairing,
  index,
  user1Profile,
  user2Profile,
  breakupInitiatorProfile,
  isActive,
  onUserClick,
  onUnpair,
  onDelete,
  formatDate,
  hasAdminActions = false,
}: PairingCardProps) => {
  // Memoize user click handlers to prevent unnecessary re-renders
  const handleUser1Click = useCallback(
    (event: React.MouseEvent) => onUserClick(pairing.user1Id, event),
    [onUserClick, pairing.user1Id]
  )

  const handleUser2Click = useCallback(
    (event: React.MouseEvent) => onUserClick(pairing.user2Id, event),
    [onUserClick, pairing.user2Id]
  )

  const handleUnpair = useCallback(
    (event: React.MouseEvent) => onUnpair?.(pairing.id, event),
    [onUnpair, pairing.id]
  )

  const handleDelete = useCallback(
    (event: React.MouseEvent) => onDelete?.(pairing.id, event),
    [onDelete, pairing.id]
  )

  // Memoize breakup information to avoid recalculation - match current implementation exactly
  const breakupInfo = useMemo(() => {
    if (!pairing.breakupReason) return null

    const isAdminBreakup = pairing.breakupInitiatorId?.startsWith('ADMIN_')
    const adminName = isAdminBreakup ? pairing.breakupInitiatorId?.replace('ADMIN_', '') : null

    return {
      isAdminBreakup,
      adminName,
      breakupInitiatorProfile,
      reason: pairing.breakupReason.length > 100 
        ? `${pairing.breakupReason.substring(0, 100)}...` 
        : pairing.breakupReason
    }
  }, [pairing.breakupReason, pairing.breakupInitiatorId, breakupInitiatorProfile])

  // Memoize card style based on active state
  const cardStyle = useMemo(() => ({
    background: 'rgba(31, 39, 49, 0.4)',
    borderColor: isActive ? 'rgba(34, 197, 94, 0.1)' : 'rgba(255, 255, 255, 0.05)'
  }), [isActive])

  // Memoize colors for performance
  const avatarRingColor = useMemo(() => 
    isActive ? 'ring-[var(--color-success)]/30' : 'ring-primary/30',
    [isActive]
  )

  const heartColor = useMemo(() => 
    isActive ? 'text-[var(--color-success)]' : 'text-primary',
    [isActive]
  )

  const chevronColor = useMemo(() => 
    isActive ? '[var(--color-success)]' : 'primary',
    [isActive]
  )

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.1 }}
      className="group p-4 rounded-xl border transition-all duration-300 hover:border-[var(--color-success)]/30 relative"
      style={cardStyle}
    >
      {/* Admin Actions */}
      {hasAdminActions && (
        <>
          {isActive && onUnpair && (
            <motion.button
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              onClick={handleUnpair}
              className="absolute top-2 right-2 p-1 rounded-full bg-[var(--color-warning)]/20 border border-[var(--color-warning)]/30 text-[var(--color-warning)] hover:bg-[var(--color-warning)]/30 transition-colors opacity-0 group-hover:opacity-100 z-10"
              title="Unpair these users (keeps blacklist)"
              aria-label="Unpair users"
            >
              <X className="h-3 w-3" />
            </motion.button>
          )}
          
          {!isActive && onDelete && (
            <motion.button
              whileHover={{ scale: 1.1 }}
              whileTap={{ scale: 0.9 }}
              onClick={handleDelete}
              className="absolute top-2 right-2 p-1 rounded-full bg-[var(--color-error)]/20 border border-[var(--color-error)]/30 text-[var(--color-error)] hover:bg-[var(--color-error)]/30 transition-colors opacity-0 group-hover:opacity-100 z-10"
              title="Permanently delete this pairing record"
              aria-label="Delete pairing"
            >
              <X className="h-3 w-3" />
            </motion.button>
          )}
        </>
      )}
      
      <div className="space-y-4">
        {/* Users Section */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {/* User 1 */}
            <motion.div
              className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
              onClick={handleUser1Click}
              whileHover={{ scale: 1.05 }}
              role="button"
              tabIndex={0}
              aria-label={`View profile of ${user1Profile?.displayName || 'user'}`}
            >
              <Avatar className={`h-8 w-8 ring-2 ${avatarRingColor}`}>
                <AvatarImage src={user1Profile?.avatar || "/placeholder.svg"} />
                <AvatarFallback className={`${isActive ? 'bg-[var(--color-success)]/20 text-[var(--color-success)]' : 'bg-primary/20 text-primary'}`}>
                  {user1Profile?.displayName?.[0] || user1Profile?.username?.[0] || "?"}
                </AvatarFallback>
              </Avatar>
              <span className="text-[var(--color-text-primary)] font-medium text-sm">
                {user1Profile?.displayName || user1Profile?.username || "Unknown"}
              </span>
            </motion.div>

            <Heart className={`h-4 w-4 ${heartColor}`} />

            {/* User 2 */}
            <motion.div
              className="flex items-center gap-2 cursor-pointer hover:bg-[var(--color-container-bg)]/80 p-2 rounded-lg transition-colors"
              onClick={handleUser2Click}
              whileHover={{ scale: 1.05 }}
              role="button"
              tabIndex={0}
              aria-label={`View profile of ${user2Profile?.displayName || 'user'}`}
            >
              <Avatar className={`h-8 w-8 ring-2 ${avatarRingColor}`}>
                <AvatarImage src={user2Profile?.avatar || "/placeholder.svg"} />
                <AvatarFallback className={`${isActive ? 'bg-[var(--color-success)]/20 text-[var(--color-success)]' : 'bg-primary/20 text-primary'}`}>
                  {user2Profile?.displayName?.[0] || user2Profile?.username?.[0] || "?"}
                </AvatarFallback>
              </Avatar>
              <span className="text-[var(--color-text-primary)] font-medium text-sm">
                {user2Profile?.displayName || user2Profile?.username || "Unknown"}
              </span>
            </motion.div>
          </div>

          <ChevronRight className={`h-4 w-4 text-[var(--color-text-tertiary)] group-hover:text-${chevronColor} transition-colors`} />
        </div>

        {/* Breakup Information for Inactive Pairings - Match current implementation exactly */}
        {breakupInfo && (
          <div className="p-3 bg-[var(--color-error)]/5 border border-[var(--color-error)]/10 rounded-lg">
            <div className="space-y-2">
              {/* Who initiated the breakup */}
              <div className="flex items-center gap-2 text-xs">
                <AlertCircle className="h-3 w-3 text-[var(--color-error)]" />
                <span className="text-[var(--color-text-secondary)]">
                  {breakupInfo.isAdminBreakup ? (
                    <span className="text-[var(--color-warning)]">
                      Ended by Admin: {breakupInfo.adminName}
                    </span>
                  ) : breakupInfo.breakupInitiatorProfile ? (
                    <span>
                      Ended by: <span className="text-[var(--color-text-primary)] font-medium">
                        {breakupInfo.breakupInitiatorProfile.displayName || breakupInfo.breakupInitiatorProfile.username}
                      </span>
                    </span>
                  ) : (
                    <span className="text-[var(--color-text-tertiary)]">
                      Initiator unknown
                    </span>
                  )}
                </span>
                {pairing.breakupTimestamp && (
                  <span className="text-[var(--color-text-tertiary)]">
                    • {formatDate(pairing.breakupTimestamp)}
                  </span>
                )}
              </div>
              
              {/* Breakup reason */}
              <div className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
                <span className="font-medium text-[var(--color-text-primary)]">Reason:</span>{" "}
                <span className="italic">{breakupInfo.reason}</span>
              </div>
            </div>
          </div>
        )}

        {/* Engagement Metrics for Active Pairings */}
        {isActive && (
          <div className="flex items-center gap-2 flex-wrap">
            {/* Message Count Badge */}
            <motion.div 
              className="inline-flex items-center gap-2 px-3 py-1.5 bg-[var(--color-info)]/10 hover:bg-[var(--color-info)]/15 border border-[var(--color-info)]/20 rounded-full transition-colors duration-200 cursor-default"
              whileHover={{ scale: 1.02 }}
              transition={{ type: "spring", stiffness: 400, damping: 25 }}
            >
              <MessageSquare className="h-3.5 w-3.5 text-[var(--color-info)]" />
              <span className="text-xs font-medium text-[var(--color-text-primary)]">
                {pairing.messageCount}
              </span>
            </motion.div>

            {/* Voice Time Badge */}
            <motion.div 
              className="inline-flex items-center gap-2 px-3 py-1.5 bg-[var(--color-warning)]/10 hover:bg-[var(--color-warning)]/15 border border-[var(--color-warning)]/20 rounded-full transition-colors duration-200 cursor-default"
              whileHover={{ scale: 1.02 }}
              transition={{ type: "spring", stiffness: 400, damping: 25 }}
            >
              <Mic className="h-3.5 w-3.5 text-[var(--color-warning)]" />
              <span className="text-xs font-medium text-[var(--color-text-primary)]">
                {Math.floor(pairing.voiceTimeMinutes / 60)}h {pairing.voiceTimeMinutes % 60}m
              </span>
            </motion.div>
          </div>
        )}

        {/* Stats and Badges */}
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            {pairing.mutualBreakup && (
              <Badge variant="outline" className="text-xs border-[var(--color-info)]/30 text-[var(--color-info)]">
                Mutual
              </Badge>
            )}
          </div>
          <div className="text-[var(--color-text-tertiary)]">
            <p className="text-right">{pairing.activeDays} days{isActive ? " active" : ""}</p>
          </div>
        </div>
      </div>
    </motion.div>
  )
})

PairingCard.displayName = 'PairingCard'

// List component that handles pagination internally
export const PairingCardList = memo(({
  pairings,
  userProfiles,
  isActive,
  onUserClick,
  onUnpair,
  onDelete,
  formatDate,
  hasAdminActions = false,
  maxItems = 5,
  emptyMessage = "No matches found",
  emptyIcon
}: PairingCardListProps) => {
  const displayedPairings = useMemo(() => 
    pairings.slice(0, maxItems), 
    [pairings, maxItems]
  )

  if (displayedPairings.length === 0) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center py-12"
      >
        {emptyIcon || (
          <div className="mx-auto w-16 h-16 bg-gradient-to-br from-[var(--color-success)]/20 to-primary/20 rounded-full flex items-center justify-center mb-4">
            <UserCheck className="h-8 w-8 text-[var(--color-success)]" />
          </div>
        )}
        <h3 className="text-lg font-semibold text-[var(--color-text-primary)] mb-2">
          {emptyMessage}
        </h3>
      </motion.div>
    )
  }

  return (
    <div className="space-y-4">
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
            formatDate={formatDate}
            hasAdminActions={hasAdminActions}
          />
        )
      })}
    </div>
  )
})

PairingCardList.displayName = 'PairingCardList' 