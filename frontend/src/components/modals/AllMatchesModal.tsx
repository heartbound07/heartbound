import type { UserProfileDTO } from "@/config/userService"
import { AnimatePresence, motion } from "framer-motion"
import { createPortal } from "react-dom"
import React, { useMemo, useCallback } from "react"
import type { PairingDTO } from "@/config/pairingService"
import { Heart, Star, Award, Trophy, MessageSquare, Mic, Calendar } from "lucide-react"
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/valorant/avatar"
import { formatVoiceTime } from "@/utils/formatters"
import "@/assets/AllMatchesModal.css"

interface AllMatchesModalProps {
  isOpen: boolean
  onClose: () => void
  pairings: PairingDTO[]
  userProfiles: Record<string, UserProfileDTO>
  onUserClick: (userId: string, event: React.MouseEvent) => void
}

const getRankIcon = (rank: number) => {
  if (rank === 0) return <Trophy className="gold" />
  if (rank === 1) return <Award className="silver" />
  if (rank === 2) return <Star className="bronze" />
  return null
}

// Optimized animation variants defined outside component to prevent recreation
const MODAL_VARIANTS = {
  overlay: {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 },
    transition: { duration: 0.2, ease: "easeOut" }
  },
  container: {
    initial: { scale: 0.95, opacity: 0 },
    animate: { scale: 1, opacity: 1 },
    exit: { scale: 0.95, opacity: 0 },
    transition: { duration: 0.25, ease: "easeOut" }
  },
  list: {
    visible: { 
      transition: { 
        staggerChildren: 0.02, // Reduced stagger for better performance
        delayChildren: 0.1 
      } 
    }
  },
  row: {
    hidden: { opacity: 0, y: 10 }, // Reduced movement for smoother animation
    visible: { 
      opacity: 1, 
      y: 0,
      transition: { 
        duration: 0.25, 
        ease: "easeOut" 
      }
    }
  }
}

const AllMatchesModalRow = React.memo(
  ({
    pairing,
    rank,
    user1,
    user2,
    onUserClick,
  }: {
    pairing: PairingDTO
    rank: number
    user1?: UserProfileDTO
    user2?: UserProfileDTO
    onUserClick: (userId: string, event: React.MouseEvent) => void
  }) => {
    const handleUser1Click = useCallback(
      (event: React.MouseEvent) => onUserClick(pairing.user1Id, event),
      [onUserClick, pairing.user1Id],
    )

    const handleUser2Click = useCallback(
      (event: React.MouseEvent) => onUserClick(pairing.user2Id, event),
      [onUserClick, pairing.user2Id],
    )

    return (
      <motion.div
        className="all-matches-row"
        variants={MODAL_VARIANTS.row}
        style={{ willChange: 'transform, opacity' }} // Optimize for animations
      >
        <div className="all-matches-rank">
          <span className="rank-number">{rank + 1}</span>
          {getRankIcon(rank)}
        </div>
        <div className="all-matches-users">
          <div className="user-profile" onClick={handleUser1Click}>
            <Avatar className="all-matches-avatar">
              <AvatarImage src={user1?.avatar} />
              <AvatarFallback>{user1?.displayName?.[0] || "?"}</AvatarFallback>
            </Avatar>
            <span className="username">{user1?.displayName || "User 1"}</span>
          </div>
          <Heart className="heart-icon" />
          <div className="user-profile" onClick={handleUser2Click}>
            <Avatar className="all-matches-avatar">
              <AvatarImage src={user2?.avatar} />
              <AvatarFallback>{user2?.displayName?.[0] || "?"}</AvatarFallback>
            </Avatar>
            <span className="username">{user2?.displayName || "User 2"}</span>
          </div>
        </div>
        <div className="all-matches-metrics">
          <div className="metric-item">
            <Calendar size={14} />
            <span>{pairing.activeDays}d</span>
          </div>
          <div className="metric-item">
            <MessageSquare size={14} />
            <span>{pairing.messageCount}</span>
          </div>
          <div className="metric-item">
            <Mic size={14} />
            <span>{formatVoiceTime(pairing.voiceTimeMinutes)}</span>
          </div>
        </div>
      </motion.div>
    )
  },
)

AllMatchesModalRow.displayName = 'AllMatchesModalRow'

export const AllMatchesModal = React.memo(({ isOpen, onClose, pairings, userProfiles, onUserClick }: AllMatchesModalProps) => {
  const sortedPairings = useMemo(() => {
    return [...pairings].sort((a, b) => b.activeDays - a.activeDays)
  }, [pairings])

  // Memoize close handler to prevent unnecessary re-renders
  const handleClose = useCallback(() => {
    onClose()
  }, [onClose])

  const handleOverlayClick = useCallback((e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      handleClose()
    }
  }, [handleClose])

  const handleContainerClick = useCallback((e: React.MouseEvent) => {
    e.stopPropagation()
  }, [])

  if (!isOpen) return null

  return createPortal(
    <AnimatePresence mode="wait">
      <motion.div
        key="all-matches-modal"
        className="all-matches-overlay"
        variants={MODAL_VARIANTS.overlay}
        initial="initial"
        animate="animate"
        exit="exit"
        onClick={handleOverlayClick}
        style={{ willChange: 'opacity' }} // Optimize for fade animations
      >
        <motion.div
          className="all-matches-container"
          variants={MODAL_VARIANTS.container}
          initial="initial"
          animate="animate"
          exit="exit"
          onClick={handleContainerClick}
          style={{ willChange: 'transform, opacity' }} // Optimize for scale animations
        >
          <div className="all-matches-header">
            <h2 className="all-matches-title">Current Matches Leaderboard</h2>
            <p className="all-matches-subtitle">Top pairs ranked by active days</p>
          </div>
          <motion.div
            className="all-matches-list"
            variants={MODAL_VARIANTS.list}
            initial="hidden"
            animate="visible"
          >
            {sortedPairings.length > 0 ? (
              sortedPairings.map((pairing, index) => (
                <AllMatchesModalRow
                  key={pairing.id}
                  pairing={pairing}
                  rank={index}
                  user1={userProfiles[pairing.user1Id]}
                  user2={userProfiles[pairing.user2Id]}
                  onUserClick={onUserClick}
                />
              ))
            ) : (
              <div className="all-matches-empty">
                <p>No active matches right now.</p>
              </div>
            )}
          </motion.div>
        </motion.div>
      </motion.div>
    </AnimatePresence>,
    document.body,
  )
})

AllMatchesModal.displayName = 'AllMatchesModal' 