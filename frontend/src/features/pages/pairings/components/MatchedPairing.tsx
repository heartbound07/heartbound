"use client"

import type React from "react"
import { memo, useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { UserCheck, User, Users, MapPin, Trophy, Calendar, MessageCircle, MessageSquare, AlertCircle } from 'lucide-react'
import { motion } from "framer-motion"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"

// Import component-specific CSS
import "@/assets/MatchedPairing.css"

// Constants for display formatting
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

export const MatchedPairing = memo(({
  currentPairing,
  pairedUser,
  user,
  actionLoading,
  onUserClick,
  onBreakup,
  formatDate
}: MatchedPairingProps) => {
  // Get partner ID
  const partnerId = useMemo(() => {
    return currentPairing?.user1Id === user?.id 
      ? currentPairing?.user2Id 
      : currentPairing?.user1Id
  }, [currentPairing, user?.id])

  // Get partner stats
  const partnerStats = useMemo(() => {
    if (!currentPairing || !user?.id) return null
    
    const isUser1 = currentPairing.user1Id === user.id
    return {
      age: isUser1 ? currentPairing.user2Age : currentPairing.user1Age,
      gender: isUser1 ? currentPairing.user2Gender : currentPairing.user1Gender,
      region: isUser1 ? currentPairing.user2Region : currentPairing.user1Region,
      rank: isUser1 ? currentPairing.user2Rank : currentPairing.user1Rank,
      messageCount: isUser1 ? currentPairing.user2MessageCount : currentPairing.user1MessageCount
    }
  }, [currentPairing, user?.id])

  // Get user's own message count
  const userMessageCount = useMemo(() => {
    if (!currentPairing || !user?.id) return 0
    
    const isUser1 = currentPairing.user1Id === user.id
    return isUser1 ? currentPairing.user1MessageCount : currentPairing.user2MessageCount
  }, [currentPairing, user?.id])

  if (!currentPairing) {
    return null
  }

  return (
    <div className="matched-pairing-wrapper">
      <motion.div
        key="paired"
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: 20 }}
        transition={{ duration: 0.5 }}
      >
        <Card className="active-pairing-card">
        <CardHeader className="pb-4">
          <CardTitle className="flex items-center gap-3 text-status-success">
            <div className="p-2 bg-status-success/20 rounded-lg">
              <UserCheck className="h-6 w-6" />
            </div>
            You're Matched!
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-6">
            {/* Partner Profile */}
            <div className="partner-profile-container flex items-center gap-4 p-4 rounded-xl theme-transition">
              <motion.div 
                whileHover={{ scale: 1.1 }} 
                transition={{ type: "spring", stiffness: 300 }}
                className="cursor-pointer"
                onClick={(e) => {
                  if (partnerId) {
                    onUserClick(partnerId, e)
                  }
                }}
              >
                <Avatar className="partner-avatar h-16 w-16">
                  <AvatarImage
                    src={pairedUser?.avatar || "/placeholder.svg"}
                    alt={pairedUser?.displayName}
                  />
                  <AvatarFallback className="bg-status-success/20 text-status-success text-xl font-bold">
                    {pairedUser?.displayName?.charAt(0) || "?"}
                  </AvatarFallback>
                </Avatar>
              </motion.div>

              <div className="flex-1">
                <h3 className="text-xl font-bold text-white mb-2">
                  {pairedUser?.displayName || "Your Match"}
                </h3>

                {/* Match Stats */}
                <div className="match-stats-grid grid grid-cols-2 md:grid-cols-4 gap-3">
                  <div className="match-stat-badge flex items-center gap-2 p-2 rounded-lg theme-transition">
                    <User className="stat-age-icon h-4 w-4" />
                    <span className="text-sm font-medium text-theme-secondary">
                      {partnerStats?.age}
                    </span>
                  </div>

                  <div className="match-stat-badge flex items-center gap-2 p-2 rounded-lg theme-transition">
                    <Users className="stat-gender-icon h-4 w-4" />
                    <span className="text-sm font-medium text-theme-secondary">
                      {GENDERS.find((g) => g.value === partnerStats?.gender)?.label || "Not specified"}
                    </span>
                  </div>

                  <div className="match-stat-badge flex items-center gap-2 p-2 rounded-lg theme-transition">
                    <MapPin className="stat-region-icon h-4 w-4" />
                    <span className="text-sm font-medium text-theme-secondary">
                      {REGIONS.find((r) => r.value === partnerStats?.region)?.label || "Not specified"}
                    </span>
                  </div>

                  <div className="match-stat-badge flex items-center gap-2 p-2 rounded-lg theme-transition">
                    <Trophy className="stat-rank-icon h-4 w-4" />
                    <span className="text-sm font-medium text-theme-secondary">
                      {RANKS.find((r) => r.value === partnerStats?.rank)?.label || "Not specified"}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Match Details */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="match-detail-item flex items-center gap-3 p-3 rounded-lg">
                <Calendar className="h-5 w-5 text-primary" />
                <div>
                  <p className="text-sm text-theme-secondary">Matched</p>
                  <p className="text-white font-medium">
                    {formatDate(currentPairing.matchedAt)}
                  </p>
                </div>
              </div>
              <div className="match-detail-item flex items-center gap-3 p-3 rounded-lg">
                <MessageCircle className="h-5 w-5 text-status-success" />
                <div className="flex-1">
                  <p className="text-sm text-theme-secondary">Discord Channel</p>
                  <a
                    href="https://discord.com/channels/1161658340418523166/1381698742721187930"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="discord-link font-medium flex items-center gap-2 group"
                  >
                    <span>#pairing-chat</span>
                    <svg
                      className="h-4 w-4 opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2} 
                        d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                      />
                    </svg>
                  </a>
                </div>
              </div>
            </div>

            {/* Detailed Message Metrics (Private View for Paired Users) */}
            <div className="activity-container p-4 rounded-xl theme-transition">
              <div className="flex items-center gap-3 mb-4">
                <MessageSquare className="h-5 w-5 text-status-info" />
                <h3 className="text-lg font-semibold text-white">Activity</h3>
              </div>
              
              <div className="activity-metrics-grid grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                {/* Your Messages */}
                <div className="activity-metric metric-user-messages text-center p-3 rounded-lg">
                  <div className="text-2xl font-bold text-status-success mb-1">
                    {userMessageCount}
                  </div>
                  <div className="text-sm text-theme-secondary">Your Messages</div>
                </div>
                
                {/* Partner's Messages */}
                <div className="activity-metric metric-partner-messages text-center p-3 rounded-lg">
                  <div className="text-2xl font-bold text-primary mb-1">
                    {partnerStats?.messageCount || 0}
                  </div>
                  <div className="text-sm text-theme-secondary">
                    {pairedUser?.displayName || "Partner"}'s Messages
                  </div>
                </div>
                
                {/* Total Messages */}
                <div className="activity-metric metric-total-messages text-center p-3 rounded-lg">
                  <div className="text-2xl font-bold text-status-info mb-1">
                    {currentPairing?.messageCount || 0}
                  </div>
                  <div className="text-sm text-theme-secondary">Total Messages</div>
                </div>
                
                {/* Voice Time */}
                <div className="activity-metric metric-voice-time text-center p-3 rounded-lg">
                  <div className="text-2xl font-bold text-status-warning mb-1">
                    {Math.floor((currentPairing?.voiceTimeMinutes || 0) / 60)}h {(currentPairing?.voiceTimeMinutes || 0) % 60}m
                  </div>
                  <div className="text-sm text-theme-secondary">Voice Time</div>
                </div>
              </div>
            </div>

            {/* Breakup Button */}
            <div className="pt-4 border-t border-theme">
              <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                <Button
                  onClick={onBreakup}
                  disabled={actionLoading}
                  variant="outline"
                  className="breakup-button w-full"
                >
                  <AlertCircle className="h-4 w-4 mr-2" />
                  End This Match
                </Button>
              </motion.div>
            </div>
          </div>
        </CardContent>
      </Card>
    </motion.div>
    </div>
  )
})

MatchedPairing.displayName = 'MatchedPairing' 