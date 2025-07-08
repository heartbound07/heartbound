"use client"

import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Volume2, BarChart3, Hash, RefreshCw, AlertCircle, Fish } from "lucide-react"
import {
  getCurrentUserProfile,
  type UserProfileDTO,
  getCombinedDailyActivity,
  type CombinedDailyActivityDTO,
  getLeaderboardUsers,
} from "@/config/userService"
import { DailyActivityChart } from "./components/DailyActivityChart"
import { Button } from "@/components/ui/button"
import { 
  SkeletonDashboardStats, 
  SkeletonDashboardActivity, 
  SkeletonDashboardChart 
} from "@/components/ui/SkeletonUI"
import { LevelCard } from "./components/LevelCard"

// Legend state interface
interface LegendState {
  messages: boolean;
  voiceMinutes: boolean;
}

/**
 * DashboardPage - Discord-style card layout inspired design
 *
 * Mimics the Discord statistics card layout while maintaining all functionality
 * Uses XPCard's dark theme color palette for consistency
 */
export function DashboardPage() {
  const [error] = useState<string | null>(null)

  // State for user profile data
  const [userProfile, setUserProfile] = useState<UserProfileDTO | null>(null)
  const [statsLoading, setStatsLoading] = useState(true)
  const [statsError, setStatsError] = useState<string | null>(null)

  // State for daily activity chart data
  const [activityData, setActivityData] = useState<CombinedDailyActivityDTO[]>([])
  const [activityLoading, setActivityLoading] = useState(true)
  const [activityError, setActivityError] = useState<string | null>(null)

  // Add state for leaderboard data to calculate ranks
  const [messageLeaderboard, setMessageLeaderboard] = useState<UserProfileDTO[]>([])
  const [voiceLeaderboard, setVoiceLeaderboard] = useState<UserProfileDTO[]>([])

  // Legend state management for chart
  const [activeLegend, setActiveLegend] = useState<LegendState>({
    messages: true,
    voiceMinutes: true
  });

  // Toggle legend item visibility
  const toggleLegendItem = (key: keyof LegendState) => {
    setActiveLegend(prev => ({
      ...prev,
      [key]: !prev[key]
    }));
  };

  // Minimum loading time in milliseconds (same pattern as ShopPage)
  const MIN_LOADING_TIME = 800

  // Fetch user profile data
  const fetchUserProfile = async () => {
    // Record the start time (same pattern as ShopPage)
    const startTime = Date.now()
    
    try {
      setStatsLoading(true)
      setStatsError(null)
      const profile = await getCurrentUserProfile()
      setUserProfile(profile)
    } catch (error) {
      console.error("Error fetching user profile:", error)
      setStatsError("Failed to load user statistics")
    } finally {
      // Calculate elapsed time and ensure minimum loading time
      const elapsedTime = Date.now() - startTime
      
      if (elapsedTime < MIN_LOADING_TIME) {
        setTimeout(() => {
          setStatsLoading(false)
        }, MIN_LOADING_TIME - elapsedTime)
      } else {
        setStatsLoading(false)
      }
    }
  }

  // Fetch leaderboard data for ranking calculations
  const fetchLeaderboardData = async () => {
    try {
      // Fetch both message and voice leaderboards in parallel
      const [messageUsers, voiceUsers] = await Promise.all([
        getLeaderboardUsers('messages'),
        getLeaderboardUsers('voice')
      ])
      
      setMessageLeaderboard(messageUsers)
      setVoiceLeaderboard(voiceUsers)
    } catch (error) {
      console.error("Error fetching leaderboard data:", error)
      // Don't set error state since this is supplementary data for ranking
    }
  }

  useEffect(() => {
    fetchUserProfile()
    fetchLeaderboardData()
  }, [])

  useEffect(() => {
    fetchActivityData()
  }, [])

  // Fetch daily activity data
  const fetchActivityData = async () => {
    // Record the start time (same pattern as ShopPage)
    const startTime = Date.now()
    
    try {
      setActivityLoading(true)
      setActivityError(null)
      const data = await getCombinedDailyActivity(30) // Last 30 days
      setActivityData(data)
    } catch (error) {
      console.error("Error fetching daily activity:", error)
      setActivityError("Failed to load activity data")
    } finally {
      // Calculate elapsed time and ensure minimum loading time
      const elapsedTime = Date.now() - startTime
      
      if (elapsedTime < MIN_LOADING_TIME) {
        setTimeout(() => {
          setActivityLoading(false)
        }, MIN_LOADING_TIME - elapsedTime)
      } else {
        setActivityLoading(false)
      }
    }
  }

  const retryFetch = () => {
    fetchUserProfile()
    fetchActivityData()
    fetchLeaderboardData()
  }

  // Calculate user ranks using the same approach as UserRankCard
  const calculateUserRanks = () => {
    if (!userProfile) return { messageRank: null, voiceRank: null }
    
    const messageRank = messageLeaderboard.findIndex(user => user.id === userProfile.id) + 1
    const voiceRank = voiceLeaderboard.findIndex(user => user.id === userProfile.id) + 1
    
    return {
      messageRank: messageRank > 0 ? messageRank : null,
      voiceRank: voiceRank > 0 ? voiceRank : null
    }
  }

  const { messageRank, voiceRank } = calculateUserRanks()

  // Format large numbers (e.g., 1000 -> 1k)
  const formatNumber = (num: number) => {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + "M"
    }
    if (num >= 1000) {
      return (num / 1000).toFixed(1) + "k"
    }
    return num.toString()
  }

  // Format minutes to readable time format
  const formatVoiceTime = (minutes: number) => {
    if (minutes === 0) return "0 mins"
    
    const hours = Math.floor(minutes / 60)
    const remainingMinutes = minutes % 60
    
    if (hours === 0) {
      return `${remainingMinutes} mins`
    } else if (remainingMinutes === 0) {
      return hours === 1 ? `${hours} hour` : `${hours} hours`
    } else {
      const hourText = hours === 1 ? "hour" : "hours"
      return `${hours}.${Math.round((remainingMinutes / 60) * 10)} ${hourText}`
    }
  }

  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="container mx-auto px-4 py-8 max-w-6xl">
        {/* Refresh Button - Top Right Corner */}
        <div className="absolute top-4 right-4 z-10">
          <Button
            onClick={retryFetch}
            variant="outline"
            size="sm"
            className="refresh-btn"
            disabled={statsLoading || activityLoading}
          >
            <RefreshCw className={`h-4 w-4 ${statsLoading || activityLoading ? "animate-spin" : ""}`} />
          </Button>
        </div>

        {/* Hero Section - New Title and Greeting */}
        <motion.div className="section-header mb-12 text-center">
          <motion.h1 
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.3, type: "spring" }}
            className="font-grandstander text-4xl md:text-5xl text-primary mb-6"
            style={{ 
              WebkitTextFillColor: 'unset',
              backgroundClip: 'unset',
              WebkitBackgroundClip: 'unset'
            }}
          >
            Dashboard
          </motion.h1>
          
          <motion.div
            className="text-xl text-theme-secondary max-w-2xl mx-auto leading-relaxed overflow-hidden"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.5, duration: 0.3 }}
          >
            <div className="relative inline-block">
              <motion.p 
                initial={{ 
                  clipPath: "inset(0 100% 0 0)",
                }}
                animate={{ 
                  clipPath: [
                    "inset(0 100% 0 0)",
                    "inset(0 0% 0 0)",
                    "inset(0 0% 0 0)",
                    "inset(0 0% 0 100%)"
                  ],
                }}
                transition={{ 
                  delay: 0.8,
                  duration: 5.5,
                  times: [0, 0.45, 0.82, 1],
                  ease: ["easeOut", "linear", "easeIn"]
                }}
                className="whitespace-nowrap inline-block"
              >
                Hello, {userProfile?.displayName || userProfile?.username || "User"}!
              </motion.p>
              <motion.span
                initial={{ visibility: "hidden" }}
                animate={{ 
                  visibility: ["hidden", "visible", "visible", "hidden"]
                }}
                transition={{
                  delay: 0.8,
                  duration: 2.5,
                  times: [0, 0.02, 0.98, 1],
                  repeat: 0
                }}
                className="inline-block w-0.5 h-6 bg-theme-secondary ml-1 align-middle typewriter-cursor"
              />
            </div>
          </motion.div>
        </motion.div>

        {/* Dashboard Content Wrapper */}
        <div className="discord-dashboard">
          {/* Level Card - User Profile Header */}
          <LevelCard 
            userProfile={userProfile}
            loading={statsLoading}
            error={statsError}
          />

          {/* Main Stats Grid */}
          {statsLoading ? (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.1 }}
              className="mb-6"
            >
              <SkeletonDashboardStats theme="dashboard" />
            </motion.div>
          ) : (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
              className="stats-container"
            >
              {/* Server Ranking Section */}
              <div className="stats-section">
                <div className="section-header">
                  <BarChart3 className="h-5 w-5" />
                  <h2>Server Ranking</h2>
                </div>
                <div className="rank-cards">
                  <AnimatePresence mode="wait">
                    {statsError ? (
                      <>
                        <div className="rank-card error">
                          <div className="rank-label">Messages</div>
                          <div className="rank-value">--</div>
                        </div>
                        <div className="rank-card error">
                          <div className="rank-label">Voice Activity</div>
                          <div className="rank-value">--</div>
                        </div>
                      </>
                    ) : (
                      <>
                        <motion.div
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          className="rank-card"
                        >
                          <div className="rank-label">Messages</div>
                          <div className="rank-value">#{messageRank || '--'}</div>
                        </motion.div>
                        <motion.div
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          transition={{ delay: 0.1 }}
                          className="rank-card"
                        >
                          <div className="rank-label">Voice Activity</div>
                          <div className="rank-value">#{voiceRank || '--'}</div>
                        </motion.div>
                      </>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Messages Section */}
              <div className="stats-section">
                <div className="section-header">
                  <Hash className="h-5 w-5" />
                  <h2>Messages</h2>
                </div>
                <div className="time-period-cards">
                  <AnimatePresence mode="wait">
                    {statsError ? (
                      <>
                        {["1d", "7d", "14d"].map((period) => (
                          <div key={period} className="time-card error">
                            <div className="time-period">{period}</div>
                            <div className="time-value">-- messages</div>
                          </div>
                        ))}
                      </>
                    ) : (
                      <>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.1 }}
                          className="time-card"
                        >
                          <div className="time-period">1d</div>
                          <div className="time-value">
                            {formatNumber(userProfile?.messagesToday || 0)} <span>messages</span>
                          </div>
                        </motion.div>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.2 }}
                          className="time-card"
                        >
                          <div className="time-period">7d</div>
                          <div className="time-value">
                            {formatNumber(userProfile?.messagesThisWeek || 0)} <span>messages</span>
                          </div>
                        </motion.div>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.3 }}
                          className="time-card"
                        >
                          <div className="time-period">14d</div>
                          <div className="time-value">
                            {formatNumber(userProfile?.messagesThisTwoWeeks || 0)} <span>messages</span>
                          </div>
                        </motion.div>
                      </>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Voice Activity Section */}
              <div className="stats-section">
                <div className="section-header">
                  <Volume2 className="h-5 w-5" />
                  <h2>Voice Activity</h2>
                </div>
                <div className="time-period-cards">
                  <AnimatePresence mode="wait">
                    {statsError ? (
                      <>
                        {["1d", "7d", "14d"].map((period) => (
                          <div key={period} className="time-card error">
                            <div className="time-period">{period}</div>
                            <div className="time-value">-- hours</div>
                          </div>
                        ))}
                      </>
                    ) : (
                      <>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.1 }}
                          className="time-card"
                        >
                          <div className="time-period">1d</div>
                          <div className="time-value">
                            {formatVoiceTime(userProfile?.voiceTimeMinutesToday || 0)}
                          </div>
                        </motion.div>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.2 }}
                          className="time-card"
                        >
                          <div className="time-period">7d</div>
                          <div className="time-value">
                            {formatVoiceTime(userProfile?.voiceTimeMinutesThisWeek || 0)}
                          </div>
                        </motion.div>
                        <motion.div
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: 0.3 }}
                          className="time-card"
                        >
                          <div className="time-period">14d</div>
                          <div className="time-value">
                            {formatVoiceTime(userProfile?.voiceTimeMinutesThisTwoWeeks || 0)}
                          </div>
                        </motion.div>
                      </>
                    )}
                  </AnimatePresence>
                </div>
              </div>
            </motion.div>
          )}

          {/* Bottom Section */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="bottom-section"
          >
            {/* Activity Overview */}
            {statsLoading ? (
              <SkeletonDashboardActivity theme="dashboard" />
            ) : (
              <div className="activity-overview">
                <div className="section-header">
                  <BarChart3 className="h-5 w-5" />
                  <h2>Activity Overview</h2>
                </div>
                <div className="activity-summary">
                  <div className="activity-item">
                    <Hash className="h-4 w-4" />
                    <span className="activity-label">Total Messages</span>
                    <span className="activity-value">{formatNumber(userProfile?.messageCount || 0)} messages</span>
                  </div>
                  <div className="activity-item">
                    <Volume2 className="h-4 w-4" />
                    <span className="activity-label">Total Voice Time</span>
                    <span className="activity-value">{formatVoiceTime(userProfile?.voiceTimeMinutesTotal || 0)}</span>
                  </div>
                  <div className="activity-item">
                    <Fish className="h-4 w-4" />
                    <span className="activity-label">Total Fish Caught</span>
                    <span className="activity-value">{formatNumber(userProfile?.fishCaughtCount || 0)} fish</span>
                  </div>
                </div>
              </div>
            )}

            {/* Charts Section */}
            {activityLoading ? (
              <SkeletonDashboardChart theme="dashboard" />
            ) : (
              <div className="charts-section">
                <div className="charts-section-header">
                  <div className="section-header">
                    <BarChart3 className="h-5 w-5" />
                    <h2>Charts</h2>
                  </div>
                  <div className="chart-legend">
                    <div 
                      className={`legend-item cursor-pointer transition-opacity duration-200 ${
                        activeLegend.messages ? 'opacity-100' : 'opacity-50'
                      }`}
                      onClick={() => toggleLegendItem('messages')}
                    >
                      <div className="legend-dot message"></div>
                      <span>Message</span>
                    </div>
                    <div 
                      className={`legend-item cursor-pointer transition-opacity duration-200 ${
                        activeLegend.voiceMinutes ? 'opacity-100' : 'opacity-50'
                      }`}
                      onClick={() => toggleLegendItem('voiceMinutes')}
                    >
                      <div className="legend-dot voice"></div>
                      <span>Voice</span>
                    </div>
                  </div>
                </div>
                <div className="chart-container">
                  <DailyActivityChart 
                    data={activityData} 
                    loading={activityLoading} 
                    error={activityError} 
                    activeLegend={activeLegend}
                  />
                </div>
              </div>
            )}
          </motion.div>

          {/* Footer */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.5 }}
            className="dashboard-footer"
          >
            <span>Last Refreshed: {new Date().toLocaleDateString('en-US', { 
              weekday: 'long',
              year: 'numeric', 
              month: 'long', 
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit'
            })}</span>
          </motion.div>

          {/* Global Error */}
          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                className="global-error"
              >
                <AlertCircle className="h-5 w-5 text-red-400 mr-2" />
                {error}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}
