"use client"

import { useState } from "react"
import { motion } from "framer-motion"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { Skeleton } from "@/components/ui/SkeletonUI"
import { 
  Trophy, 
  Star, 
  Crown, 
  Award,
  MessageSquare,
  Clock,
  TrendingUp,
  Users,
  RefreshCw,
  AlertCircle
} from 'lucide-react'
import { Button } from "@/components/ui/button"
import { UserProfileModal } from "@/components/modals/UserProfileModal"
import { usePairingLeaderboard } from "@/hooks/usePairingLeaderboard"
import { getUserProfile, type UserProfileDTO } from "@/config/userService"
import { useSanitizedContent } from "@/hooks/useSanitizedContent"

interface UserPosition {
  x: number;
  y: number;
}

export function PairingLeaderboard() {
  const { data: leaderboard, loading, error, refetch } = usePairingLeaderboard()
  const [selectedUser, setSelectedUser] = useState<UserProfileDTO | null>(null)
  const [userModalPosition, setUserModalPosition] = useState<UserPosition | null>(null)
  const [isLoadingProfile, setIsLoadingProfile] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)

  // Helper function to safely render display names
  const getSafeDisplayName = (displayName: string | undefined, username: string) => {
    const { sanitized } = useSanitizedContent(displayName || username, { 
      maxLength: 50, 
      stripHtml: true 
    })
    return sanitized || username
  }

  // Handle user click for top 3 only
  const handleUserClick = async (userId: string, event: React.MouseEvent) => {
    event.preventDefault()
    event.stopPropagation()

    // Set modal position based on click location
    const rect = (event.target as HTMLElement).getBoundingClientRect()
    setUserModalPosition({ 
      x: rect.left + window.scrollX, 
      y: rect.bottom + window.scrollY + 10 
    })

    setIsLoadingProfile(true)
    setProfileError(null)

    try {
      const profile = await getUserProfile(userId)
      setSelectedUser(profile)
    } catch (err: any) {
      const errorMessage = err?.response?.data?.message || err?.message || 'Failed to load user profile'
      setProfileError(errorMessage)
      console.error('Error fetching user profile:', err)
    } finally {
      setIsLoadingProfile(false)
    }
  }

  const handleCloseModal = () => {
    setSelectedUser(null)
    setUserModalPosition(null)
    setProfileError(null)
  }

  // Medal icons for top 3
  const getMedalIcon = (rank: number) => {
    switch (rank) {
      case 1: return "🥇"
      case 2: return "🥈"
      case 3: return "🥉"
      default: return null
    }
  }

  // Format voice time for display
  const formatVoiceTime = (minutes: number) => {
    const hours = Math.floor(minutes / 60)
    const mins = minutes % 60
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`
  }

  // Top 3 podium component
  const TopThreePodium = () => {
    const topThree = leaderboard.slice(0, 3)
    
    if (topThree.length === 0) return null

    return (
      <div className="mb-8">
        <h3 className="text-xl font-bold text-white mb-6 text-center">🏆 Top Pairs 🏆</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {topThree.map((pair, index) => {
            const rank = index + 1
            const medal = getMedalIcon(rank)
            
            return (
              <motion.div
                key={pair.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.1 }}
                className={`relative ${rank === 1 ? 'md:order-2 md:scale-110' : rank === 2 ? 'md:order-1' : 'md:order-3'}`}
              >
                <Card className={`${
                  rank === 1 
                    ? 'bg-gradient-to-br from-yellow-500/20 to-orange-500/20 border-yellow-500/30' 
                    : rank === 2 
                    ? 'bg-gradient-to-br from-gray-300/20 to-gray-500/20 border-gray-400/30'
                    : 'bg-gradient-to-br from-amber-600/20 to-amber-800/20 border-amber-600/30'
                } h-full`}>
                  
                  {/* Rank Badge */}
                  <div className="absolute -top-3 left-1/2 transform -translate-x-1/2 z-10">
                    <div className={`${
                      rank === 1 ? 'bg-yellow-500' : rank === 2 ? 'bg-gray-400' : 'bg-amber-600'
                    } text-white rounded-full w-8 h-8 flex items-center justify-center text-lg font-bold`}>
                      {medal}
                    </div>
                  </div>

                  <CardHeader className="pb-4 pt-6">
                    <CardTitle className="text-center">
                      <div className="flex items-center justify-center gap-2 text-white">
                        <Crown className={`h-5 w-5 ${
                          rank === 1 ? 'text-yellow-500' : rank === 2 ? 'text-gray-400' : 'text-amber-600'
                        }`} />
                        Rank #{rank}
                      </div>
                    </CardTitle>
                  </CardHeader>

                  <CardContent className="space-y-4">
                    {/* User Profiles */}
                    <div className="flex items-center justify-center gap-4">
                      <div 
                        className="flex flex-col items-center cursor-pointer hover:scale-105 transition-transform"
                        onClick={(e) => handleUserClick(pair.user1Id, e)}
                      >
                        <Avatar className="h-12 w-12 border-2 border-white/20">
                          <AvatarImage src={pair.user1Profile.avatar} alt={pair.user1Profile.displayName} />
                          <AvatarFallback className="bg-primary/20 text-primary">
                            {pair.user1Profile.displayName?.charAt(0) || "U1"}
                          </AvatarFallback>
                        </Avatar>
                        <p className="text-sm font-medium text-white mt-1 text-center">
                          {getSafeDisplayName(pair.user1Profile.displayName, pair.user1Profile.username)}
                        </p>
                      </div>

                      <Users className="h-6 w-6 text-primary" />

                      <div 
                        className="flex flex-col items-center cursor-pointer hover:scale-105 transition-transform"
                        onClick={(e) => handleUserClick(pair.user2Id, e)}
                      >
                        <Avatar className="h-12 w-12 border-2 border-white/20">
                          <AvatarImage src={pair.user2Profile.avatar} alt={pair.user2Profile.displayName} />
                          <AvatarFallback className="bg-primary/20 text-primary">
                            {pair.user2Profile.displayName?.charAt(0) || "U2"}
                          </AvatarFallback>
                        </Avatar>
                        <p className="text-sm font-medium text-white mt-1 text-center">
                          {getSafeDisplayName(pair.user2Profile.displayName, pair.user2Profile.username)}
                        </p>
                      </div>
                    </div>

                    {/* Stats */}
                    <div className="grid grid-cols-2 gap-3 text-sm">
                      <div className="text-center p-2 rounded-lg bg-black/20">
                        <Star className="h-4 w-4 text-yellow-500 mx-auto mb-1" />
                        <div className="text-white font-bold">Level {pair.currentLevel}</div>
                        <div className="text-theme-secondary">{pair.totalXP} XP</div>
                      </div>
                      <div className="text-center p-2 rounded-lg bg-black/20">
                        <MessageSquare className="h-4 w-4 text-green-500 mx-auto mb-1" />
                        <div className="text-white font-bold">{pair.messageCount}</div>
                        <div className="text-theme-secondary">Messages</div>
                      </div>
                      <div className="text-center p-2 rounded-lg bg-black/20">
                        <Clock className="h-4 w-4 text-blue-500 mx-auto mb-1" />
                        <div className="text-white font-bold">{formatVoiceTime(pair.voiceTimeMinutes)}</div>
                        <div className="text-theme-secondary">Voice</div>
                      </div>
                      <div className="text-center p-2 rounded-lg bg-black/20">
                        <TrendingUp className="h-4 w-4 text-purple-500 mx-auto mb-1" />
                        <div className="text-white font-bold">{pair.activeDays}</div>
                        <div className="text-theme-secondary">Days</div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </motion.div>
            )
          })}
        </div>
      </div>
    )
  }

  // Remaining leaderboard list
  const LeaderboardList = () => {
    const remainingPairs = leaderboard.slice(3)
    
    if (remainingPairs.length === 0) return null

    return (
      <div>
        <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <Award className="h-5 w-5 text-primary" />
          Full Rankings
        </h3>
        <div className="space-y-3 max-h-96 overflow-y-auto">
          {remainingPairs.map((pair, index) => {
            const rank = index + 4
            
            return (
              <motion.div
                key={pair.id}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: index * 0.05 }}
                className="flex items-center gap-4 p-4 rounded-lg bg-theme-container border border-theme hover:border-primary/50 transition-colors"
              >
                {/* Rank */}
                <div className="flex-shrink-0 w-8 h-8 rounded-full bg-theme-tertiary flex items-center justify-center">
                  <span className="text-sm font-bold text-white">#{rank}</span>
                </div>

                {/* User Avatars */}
                <div className="flex items-center gap-2">
                  <Avatar className="h-8 w-8">
                    <AvatarImage src={pair.user1Profile.avatar} alt={pair.user1Profile.displayName} />
                    <AvatarFallback className="bg-primary/20 text-primary text-xs">
                      {pair.user1Profile.displayName?.charAt(0) || "U1"}
                    </AvatarFallback>
                  </Avatar>
                  <Users className="h-4 w-4 text-theme-secondary" />
                  <Avatar className="h-8 w-8">
                    <AvatarImage src={pair.user2Profile.avatar} alt={pair.user2Profile.displayName} />
                    <AvatarFallback className="bg-primary/20 text-primary text-xs">
                      {pair.user2Profile.displayName?.charAt(0) || "U2"}
                    </AvatarFallback>
                  </Avatar>
                </div>

                {/* Names */}
                <div className="flex-1 min-w-0">
                  <p className="text-white font-medium truncate">
                    {getSafeDisplayName(pair.user1Profile.displayName, pair.user1Profile.username)} & {getSafeDisplayName(pair.user2Profile.displayName, pair.user2Profile.username)}
                  </p>
                                      <p className="text-xs text-theme-secondary">
                      {pair.discordChannelName ? useSanitizedContent(pair.discordChannelName, { maxLength: 100, stripHtml: true }).sanitized : ''}
                    </p>
                </div>

                {/* Stats */}
                <div className="flex items-center gap-4 text-sm">
                  <Badge variant="outline" className="text-yellow-500 border-yellow-500/30">
                    Level {pair.currentLevel}
                  </Badge>
                  <span className="text-theme-secondary">{pair.totalXP} XP</span>
                  <span className="text-theme-secondary">{pair.messageCount} msgs</span>
                </div>
              </motion.div>
            )
          })}
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <Card className="bg-theme-container border-theme">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-white">
            <Trophy className="h-5 w-5 text-primary" />
            Active Pairings Leaderboard
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Loading skeleton for top 3 */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[1, 2, 3].map(i => (
              <div key={i} className="p-6 rounded-xl bg-theme-container border-theme">
                <Skeleton width="80px" height="20px" className="mx-auto mb-4" theme="valorant" />
                <div className="flex justify-center gap-4 mb-4">
                  <Skeleton width="48px" height="48px" className="rounded-full" theme="valorant" />
                  <Skeleton width="48px" height="48px" className="rounded-full" theme="valorant" />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Skeleton width="100%" height="60px" theme="valorant" />
                  <Skeleton width="100%" height="60px" theme="valorant" />
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card className="bg-theme-container border-theme">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-white">
            <Trophy className="h-5 w-5 text-primary" />
            Active Pairings Leaderboard
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center gap-4 py-8">
            <AlertCircle className="h-12 w-12 text-status-error" />
            <div className="text-center">
              <p className="text-white font-medium">Failed to load leaderboard</p>
              <p className="text-theme-secondary text-sm mt-1">{error}</p>
            </div>
            <Button 
              onClick={refetch}
              className="valorant-button-primary"
            >
              <RefreshCw className="h-4 w-4 mr-2" />
              Try Again
            </Button>
          </div>
        </CardContent>
      </Card>
    )
  }

  if (leaderboard.length === 0) {
    return (
      <Card className="bg-theme-container border-theme">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-white">
            <Trophy className="h-5 w-5 text-primary" />
            Active Pairings Leaderboard
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center gap-4 py-8">
            <Trophy className="h-12 w-12 text-theme-secondary" />
            <div className="text-center">
              <p className="text-white font-medium">No Active Pairings</p>
              <p className="text-theme-secondary text-sm mt-1">
                The leaderboard will appear when users start getting matched!
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      <Card className="bg-theme-container border-theme">
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-white">
              <Trophy className="h-5 w-5 text-primary" />
              Active Pairings Leaderboard
            </div>
            <Badge variant="outline" className="text-xs">
              {leaderboard.length} Pairs
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <TopThreePodium />
          <LeaderboardList />
        </CardContent>
      </Card>

      {/* User Profile Modal */}
      <UserProfileModal
        isOpen={selectedUser !== null || isLoadingProfile}
        onClose={handleCloseModal}
        userProfile={selectedUser}
        position={userModalPosition}
        error={profileError}
      />
    </>
  )
} 