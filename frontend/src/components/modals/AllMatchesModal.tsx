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
        variants={{
          hidden: { opacity: 0, y: 20 },
          visible: { opacity: 1, y: 0 },
        }}
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

export const AllMatchesModal = ({ isOpen, onClose, pairings, userProfiles, onUserClick }: AllMatchesModalProps) => {
  const sortedPairings = useMemo(() => {
    return [...pairings].sort((a, b) => b.activeDays - a.activeDays)
  }, [pairings])

  if (!isOpen) return null

  return createPortal(
    <AnimatePresence>
      <motion.div
        key="all-matches-modal"
        className="all-matches-overlay"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
      >
        <motion.div
          className="all-matches-container"
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.9, opacity: 0 }}
          transition={{ duration: 0.3, ease: "easeInOut" }}
          onClick={e => e.stopPropagation()}
        >
          <div className="all-matches-header">
            <h2 className="all-matches-title">Current Matches Leaderboard</h2>
            <p className="all-matches-subtitle">Top pairs ranked by active days</p>
          </div>
          <motion.div
            className="all-matches-list"
            variants={{
              visible: { transition: { staggerChildren: 0.05 } },
            }}
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
} 