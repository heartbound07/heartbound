"use client"

import type React from "react"
import { memo, useMemo } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import {
  Users,
  MapPin,
  Calendar,
  MessageCircle,
  ExternalLink,
  User,
  UserCheck,
  Crown,
} from "lucide-react"
import { motion } from "framer-motion"
import type { PairingDTO } from "@/config/pairingService"
import type { UserProfileDTO } from "@/config/userService"
import { Badge } from "@/components/ui/valorant/badge"

// Import component CSS
import "@/assets/GroupChannel.css"

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
  { value: "MALE", label: "Male", emoji: "♂️" },
  { value: "FEMALE", label: "Female", emoji: "♀️" },
  { value: "NON_BINARY", label: "Non-Binary", emoji: "⚧️" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say", emoji: "❓" },
] as const

interface GroupChannelProps {
  groupPairing: PairingDTO
  userProfiles: Record<string, UserProfileDTO>
  user: { id: string } | null
  onUserClick: (userId: string, event: React.MouseEvent) => void
  formatDate: (dateString: string) => string
}

export const GroupChannel = memo(
  ({ groupPairing, userProfiles, user, onUserClick, formatDate }: GroupChannelProps) => {
    // Group member data
    const groupMembers = useMemo(() => {
      if (!groupPairing.groupMembers) return []
      
      return groupPairing.groupMembers.map(member => ({
        ...member,
        profile: userProfiles[member.userId] || null,
        isCurrentUser: member.userId === user?.id
      }))
    }, [groupPairing.groupMembers, userProfiles, user?.id])

    // Sort members: current user first, then by gender (males first), then alphabetically
    const sortedMembers = useMemo(() => {
      return groupMembers.sort((a, b) => {
        // Current user first
        if (a.isCurrentUser) return -1
        if (b.isCurrentUser) return 1
        
        // Then by gender (males first)
        if (a.gender === "MALE" && b.gender === "FEMALE") return -1
        if (a.gender === "FEMALE" && b.gender === "MALE") return 1
        
        // Then alphabetically by display name
        const nameA = a.profile?.displayName || a.userId
        const nameB = b.profile?.displayName || b.userId
        return nameA.localeCompare(nameB)
      })
    }, [groupMembers])

    // Group statistics
    const groupStats = useMemo(() => {
      const totalMembers = groupPairing.totalGroupMembers || groupMembers.length
      const maleCount = groupPairing.maleCount || groupMembers.filter(m => m.gender === "MALE").length
      const femaleCount = groupPairing.femaleCount || groupMembers.filter(m => m.gender === "FEMALE").length
      const regionLabel = REGIONS.find(r => r.value === groupPairing.groupRegion)?.label || groupPairing.groupRegion
      
      return {
        totalMembers,
        maleCount,
        femaleCount,
        regionLabel
      }
    }, [groupPairing, groupMembers])

    if (!groupPairing.isGroupChannel) {
      return null
    }

    return (
      <motion.div
        className="group-channel-container"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        transition={{ duration: 0.6, ease: "easeOut" }}
      >
        <Card className="group-channel-card">
          <CardHeader className="group-header">
            <CardTitle className="group-title">
              <Users className="group-title-icon" />
              Your Group Channel
              <Badge variant="secondary" className="group-size-badge">
                {groupStats.totalMembers} members
              </Badge>
            </CardTitle>
          </CardHeader>
          
          <CardContent className="group-content">
            {/* Group Stats Section */}
            <motion.div
              className="group-stats"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.2, duration: 0.5 }}
            >
              <div className="stat-card">
                <div className="stat-icon-container male">
                  <User className="stat-icon" />
                </div>
                <div className="stat-content">
                  <span className="stat-value">{groupStats.maleCount}</span>
                  <span className="stat-label">Males</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-container female">
                  <UserCheck className="stat-icon" />
                </div>
                <div className="stat-content">
                  <span className="stat-value">{groupStats.femaleCount}</span>
                  <span className="stat-label">Females</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-container region">
                  <MapPin className="stat-icon" />
                </div>
                <div className="stat-content">
                  <span className="stat-value">{groupStats.regionLabel}</span>
                  <span className="stat-label">Region</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-icon-container date">
                  <Calendar className="stat-icon" />
                </div>
                <div className="stat-content">
                  <span className="stat-value">{formatDate(groupPairing.groupCreatedAt || groupPairing.matchedAt)}</span>
                  <span className="stat-label">Created</span>
                </div>
              </div>
            </motion.div>

            {/* Group Members Grid */}
            <motion.div
              className="members-section"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3, duration: 0.5 }}
            >
              <h4 className="members-title">Group Members</h4>
              <div className="members-grid">
                {sortedMembers.map((member, index) => {
                  const genderInfo = GENDERS.find(g => g.value === member.gender)
                  const rankInfo = RANKS.find(r => r.value === member.rank)
                  
                  return (
                    <motion.div
                      key={member.userId}
                      className={`member-card ${member.isCurrentUser ? 'current-user' : ''}`}
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: 0.4 + index * 0.1, duration: 0.3 }}
                      onClick={(e) => !member.isCurrentUser && onUserClick(member.userId, e)}
                    >
                      {member.isCurrentUser && (
                        <div className="current-user-badge">
                          <Crown className="crown-icon" />
                          You
                        </div>
                      )}
                      
                      <div className="member-avatar-container">
                        <Avatar className="member-avatar">
                          <AvatarImage 
                            src={member.profile?.avatar || "/placeholder.svg"} 
                            alt={member.profile?.displayName || member.userId} 
                          />
                          <AvatarFallback className="member-avatar-fallback">
                            {(member.profile?.displayName || member.userId).charAt(0)}
                          </AvatarFallback>
                        </Avatar>
                        <div className="gender-indicator" title={genderInfo?.label}>
                          {genderInfo?.emoji}
                        </div>
                      </div>

                      <div className="member-info">
                        <h5 className="member-name">
                          {member.profile?.displayName || member.userId}
                        </h5>
                        <div className="member-details">
                          <span className="member-age">{member.age} years old</span>
                          {rankInfo && (
                            <Badge variant="outline" className="member-rank">
                              {rankInfo.label}
                            </Badge>
                          )}
                        </div>
                      </div>
                    </motion.div>
                  )
                })}
              </div>
            </motion.div>

            {/* Discord Channel Link */}
            <motion.div
              className="discord-section"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.5 }}
            >
              <div className="discord-info">
                <div className="discord-icon-container">
                  <MessageCircle className="discord-icon" />
                </div>
                <div className="discord-content">
                  <span className="discord-label">Group Discord Channel</span>
                  {groupPairing.discordChannelId ? (
                    <a
                      href={`https://discord.com/channels/YOUR_SERVER_ID/${groupPairing.discordChannelId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="discord-link"
                    >
                      <span>#{groupPairing.discordChannelName || `group-${groupPairing.id}`}</span>
                      <ExternalLink className="external-icon" />
                    </a>
                  ) : (
                    <span className="discord-pending">Channel being created...</span>
                  )}
                </div>
              </div>
            </motion.div>

            {/* Next Steps Info */}
            <motion.div
              className="next-steps"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.6, duration: 0.5 }}
            >
              <h4 className="next-steps-title">What's Next?</h4>
              <ul className="next-steps-list">
                <li>Get to know your group members in the Discord channel</li>
                <li>If you find someone you'd like to pair with, individual pairing requests will be available soon</li>
                <li>For now, enjoy meeting new people and having fun conversations!</li>
              </ul>
            </motion.div>
          </CardContent>
        </Card>
      </motion.div>
    )
  }
)

GroupChannel.displayName = "GroupChannel" 