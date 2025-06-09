import React, { memo, useCallback, useMemo } from 'react'
import { motion } from 'framer-motion'
import { Heart, Star, ChevronRight, X, AlertCircle } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/valorant/avatar'
import { Badge } from '@/components/ui/valorant/badge'
import type { PairingDTO } from '@/config/pairingService'
import type { UserProfileDTO } from '@/config/userService'

interface PairingCardProps {
  pairing: PairingDTO
  index: number
  user1Profile?: UserProfileDTO
  user2Profile?: UserProfileDTO
  isActive: boolean
  onUserClick: (userId: string, event: React.MouseEvent) => void
  onUnpair?: (pairingId: number, event: React.MouseEvent) => void
  onDelete?: (pairingId: number, event: React.MouseEvent) => void
  formatDate: (dateString: string) => string
  hasAdminActions?: boolean
}

export const PairingCard = memo(({
  pairing,
  index,
  user1Profile,
  user2Profile,
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

  // Memoize breakup information to avoid recalculation
  const breakupInfo = useMemo(() => {
    if (!pairing.breakupReason) return null

    const isAdminBreakup = pairing.breakupInitiatorId?.startsWith('ADMIN_')
    const adminName = isAdminBreakup ? pairing.breakupInitiatorId?.replace('ADMIN_', '') : null

    return {
      isAdminBreakup,
      adminName,
      reason: pairing.breakupReason.length > 100 
        ? `${pairing.breakupReason.substring(0, 100)}...` 
        : pairing.breakupReason
    }
  }, [pairing.breakupReason, pairing.breakupInitiatorId])

  // Memoize card style based on active state
  const cardStyle = useMemo(() => ({
    background: isActive ? 'rgba(31, 39, 49, 0.4)' : 'rgba(31, 39, 49, 0.4)',
    borderColor: isActive ? 'rgba(34, 197, 94, 0.1)' : 'rgba(255, 255, 255, 0.05)'
  }), [isActive])

  // Memoize ring colors for avatars
  const avatarRingColor = useMemo(() => 
    isActive ? 'ring-[var(--color-success)]/30' : 'ring-primary/30',
    [isActive]
  )

  const heartColor = useMemo(() => 
    isActive ? 'text-[var(--color-success)]' : 'text-primary',
    [isActive]
  )

  const chevronColor = useMemo(() => 
    isActive ? 'text-[var(--color-success)]' : 'text-primary',
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
              className="absolute top-2 right-10 p-1 rounded-full bg-[var(--color-warning)]/20 border border-[var(--color-warning)]/30 text-[var(--color-warning)] hover:bg-[var(--color-warning)]/30 transition-colors opacity-0 group-hover:opacity-100 z-10"
              title="Unpair these users (keeps blacklist)"
              aria-label="Unpair users"
            >
              <X className="h-3 w-3" />
            </motion.button>
          )}
          
          {onDelete && (
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

          <ChevronRight className={`h-4 w-4 text-[var(--color-text-tertiary)] group-hover:${chevronColor} transition-colors`} />
        </div>

        {/* Breakup Information for Inactive Pairings */}
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
                  ) : (
                    <span className="text-[var(--color-text-tertiary)]">
                      Ended by user
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

        {/* Stats and Badges */}
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <Badge
              variant={isActive ? "default" : "secondary"}
              className={`text-xs ${
                isActive 
                  ? "bg-[var(--color-success)]/20 text-[var(--color-success)] border-[var(--color-success)]/30"
                  : ""
              }`}
            >
              {isActive ? "Active" : "Ended"}
            </Badge>
            <Badge variant="outline" className={`text-xs border-${isActive ? '[var(--color-success)]' : 'primary'}/30 text-${isActive ? '[var(--color-success)]' : 'primary'}`}>
              <Star className="h-3 w-3 mr-1" />
              {pairing.compatibilityScore}%
            </Badge>
            {pairing.mutualBreakup && (
              <Badge variant="outline" className="text-xs border-[var(--color-info)]/30 text-[var(--color-info)]">
                Mutual
              </Badge>
            )}
          </div>
          <div className="text-[var(--color-text-tertiary)]">
            <p>Matched: {formatDate(pairing.matchedAt)}</p>
            <p className="text-right">{pairing.activeDays} days{isActive ? " active" : ""}</p>
          </div>
        </div>
      </div>
    </motion.div>
  )
})

PairingCard.displayName = 'PairingCard' 