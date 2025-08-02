"use client"

import type React from "react"
import { memo, useMemo } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import {
  Calendar,
  MessageCircle,
  MessageSquare,
  AlertCircle,
  ExternalLink,
} from "lucide-react"
import { motion } from "framer-motion"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"

// Import redesigned component CSS
import "@/assets/MatchedPairing.css"

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
    // Get partner ID - preserved business logic
    const partnerId = useMemo(() => {
      return currentPairing?.user1Id === user?.id ? currentPairing?.user2Id : currentPairing?.user1Id
    }, [currentPairing, user?.id])

    // Get user's own message count - preserved business logic
    const userMessageCount = useMemo(() => {
      if (!currentPairing || !user?.id) return 0

      const isUser1 = currentPairing.user1Id === user.id
      return isUser1 ? currentPairing.user1MessageCount : currentPairing.user2MessageCount
    }, [currentPairing, user?.id])

    // Get partner's message count - preserved business logic
    const partnerMessageCount = useMemo(() => {
      if (!currentPairing || !user?.id) return 0

      const isUser1 = currentPairing.user1Id === user.id
      return isUser1 ? currentPairing.user2MessageCount : currentPairing.user1MessageCount
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
                    <span>#leaderboard</span>
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
                  <div className="metric-value">{partnerMessageCount}</div>
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
