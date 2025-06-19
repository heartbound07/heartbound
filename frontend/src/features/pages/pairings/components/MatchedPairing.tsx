"use client"

import type React from "react"
import { memo, useMemo, useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import {
  UserCheck,
  User,
  Users,
  MapPin,
  Trophy,
  Calendar,
  MessageCircle,
  MessageSquare,
  AlertCircle,
  ExternalLink,
} from "lucide-react"
import { motion, AnimatePresence } from "framer-motion"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"

// Import redesigned component CSS
import "@/assets/MatchedPairing.css"

// Constants for display formatting - preserved exactly
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

interface MatchedPairingProps {
  currentPairing: PairingDTO
  pairedUser: UserProfileDTO | null
  user: { id: string } | null
  actionLoading: boolean
  onUserClick: (userId: string, event: React.MouseEvent) => void
  onBreakup: () => void
  formatDate: (dateString: string) => string
}

export const MatchedPairing = memo(
  ({ currentPairing, pairedUser, user, actionLoading, onUserClick, onBreakup, formatDate }: MatchedPairingProps) => {
    // State for controlling "You're Matched!" text visibility
    const [showMatchedText, setShowMatchedText] = useState(true)

    // Timer effect for "You're Matched!" text - 5 seconds
    useEffect(() => {
      const timer = setTimeout(() => {
        setShowMatchedText(false)
      }, 5000)

      return () => clearTimeout(timer)
    }, [])

    // Get partner ID - preserved business logic
    const partnerId = useMemo(() => {
      return currentPairing?.user1Id === user?.id ? currentPairing?.user2Id : currentPairing?.user1Id
    }, [currentPairing, user?.id])

    // Get partner stats - preserved business logic
    const partnerStats = useMemo(() => {
      if (!currentPairing || !user?.id) return null

      const isUser1 = currentPairing.user1Id === user.id
      return {
        age: isUser1 ? currentPairing.user2Age : currentPairing.user1Age,
        gender: isUser1 ? currentPairing.user2Gender : currentPairing.user1Gender,
        region: isUser1 ? currentPairing.user2Region : currentPairing.user1Region,
        rank: isUser1 ? currentPairing.user2Rank : currentPairing.user1Rank,
        messageCount: isUser1 ? currentPairing.user2MessageCount : currentPairing.user1MessageCount,
      }
    }, [currentPairing, user?.id])

    // Get user's own message count - preserved business logic
    const userMessageCount = useMemo(() => {
      if (!currentPairing || !user?.id) return 0

      const isUser1 = currentPairing.user1Id === user.id
      return isUser1 ? currentPairing.user1MessageCount : currentPairing.user2MessageCount
    }, [currentPairing, user?.id])

    if (!currentPairing) {
      return null
    }

    return (
      <motion.div
        className="matched-pairing-container"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        transition={{ duration: 0.6, ease: "easeOut" }}
      >
        <Card className="matched-pairing-card">
          {/* Animated Header with 5-second display */}
          <AnimatePresence>
            {showMatchedText && (
              <motion.div
                initial={{ opacity: 0, y: -30, scale: 0.8 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -30, scale: 0.8 }}
                transition={{
                  duration: 0.8,
                  ease: [0.4, 0, 0.2, 1],
                }}
              >
                <CardHeader className="matched-header">
                  <CardTitle className="matched-title">
                    <motion.div
                      className="matched-icon-container"
                      initial={{ rotate: -180, scale: 0 }}
                      animate={{ rotate: 0, scale: 1 }}
                      transition={{ delay: 0.3, duration: 0.6, ease: "backOut" }}
                    >
                      <UserCheck className="matched-icon" />
                    </motion.div>
                    <motion.span
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.5, duration: 0.5 }}
                    >
                      You're Matched!
                    </motion.span>
                  </CardTitle>
                </CardHeader>
              </motion.div>
            )}
          </AnimatePresence>

          <CardContent className="matched-content">
            {/* Partner Profile Section */}
            <motion.div
              className="partner-section"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.2, duration: 0.5 }}
            >
              <div className="partner-profile">
                <div
                  className="partner-avatar-container"
                  onClick={(e) => {
                    if (partnerId) {
                      onUserClick(partnerId, e)
                    }
                  }}
                >
                  <Avatar className="partner-avatar">
                    <AvatarImage src={pairedUser?.avatar || "/placeholder.svg"} alt={pairedUser?.displayName} />
                    <AvatarFallback className="partner-avatar-fallback">
                      {pairedUser?.displayName?.charAt(0) || "?"}
                    </AvatarFallback>
                  </Avatar>
                </div>

                <div className="partner-info">
                  <h3 className="partner-name">{pairedUser?.displayName || "Your Match"}</h3>

                  {/* Stats grid with improved layout */}
                  <div className="stats-grid">
                    <div className="stat-item">
                      <User className="stat-icon age-icon" />
                      <span className="stat-value">{partnerStats?.age}</span>
                    </div>
                    <div className="stat-item">
                      <Users className="stat-icon gender-icon" />
                      <span className="stat-value">
                        {GENDERS.find((g) => g.value === partnerStats?.gender)?.label || "Not specified"}
                      </span>
                    </div>
                    <div className="stat-item">
                      <MapPin className="stat-icon region-icon" />
                      <span className="stat-value">
                        {REGIONS.find((r) => r.value === partnerStats?.region)?.label || "Not specified"}
                      </span>
                    </div>
                    <div className="stat-item">
                      <Trophy className="stat-icon rank-icon" />
                      <span className="stat-value">
                        {RANKS.find((r) => r.value === partnerStats?.rank)?.label || "Not specified"}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* Match Details */}
            <motion.div
              className="match-details"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3, duration: 0.5 }}
            >
              <div className="detail-item">
                <div className="detail-icon-container">
                  <Calendar className="detail-icon" />
                </div>
                <div className="detail-content">
                  <span className="detail-label">Matched</span>
                  <span className="detail-value">{formatDate(currentPairing.matchedAt)}</span>
                </div>
              </div>

              <div className="detail-item">
                <div className="detail-icon-container">
                  <MessageCircle className="detail-icon" />
                </div>
                <div className="detail-content">
                  <span className="detail-label">Discord Channel</span>
                  <a
                    href="https://discord.com/channels/1161658340418523166/1381698742721187930"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="discord-link"
                  >
                    <span>#pairing-chat</span>
                    <ExternalLink className="external-icon" />
                  </a>
                </div>
              </div>
            </motion.div>

            {/* Activity Metrics with centered text */}
            <motion.div
              className="activity-section"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4, duration: 0.5 }}
            >
              <div className="activity-header">
                <div className="activity-icon-container">
                  <MessageSquare className="activity-icon" />
                </div>
                <h3 className="activity-title">Activity</h3>
              </div>

              <div className="metrics-grid">
                <div className="metric-card user-messages">
                  <div className="metric-value">{userMessageCount}</div>
                  <div className="metric-label">Your Messages</div>
                </div>

                <div className="metric-card partner-messages">
                  <div className="metric-value">{partnerStats?.messageCount || 0}</div>
                  <div className="metric-label">{pairedUser?.displayName || "Partner"}'s Messages</div>
                </div>

                <div className="metric-card total-messages">
                  <div className="metric-value">{currentPairing?.messageCount || 0}</div>
                  <div className="metric-label">Total Messages</div>
                </div>

                <div className="metric-card voice-time">
                  <div className="metric-value">
                    {Math.floor((currentPairing?.voiceTimeMinutes || 0) / 60)}h{" "}
                    {(currentPairing?.voiceTimeMinutes || 0) % 60}m
                  </div>
                  <div className="metric-label">Voice Time</div>
                </div>
              </div>
            </motion.div>

            {/* Action Section */}
            <motion.div
              className="action-section"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.5, duration: 0.5 }}
            >
              <Button onClick={onBreakup} disabled={actionLoading} variant="outline" className="breakup-button">
                <AlertCircle className="breakup-icon" />
                End This Match
              </Button>
            </motion.div>
          </CardContent>
        </Card>
      </motion.div>
    )
  },
)

MatchedPairing.displayName = "MatchedPairing"
