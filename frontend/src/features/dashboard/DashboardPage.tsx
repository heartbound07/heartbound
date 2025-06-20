"use client"

import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { MessageSquare, Volume2, BarChart3, Hash, User, RefreshCw, AlertCircle } from "lucide-react"
import {
  getCurrentUserProfile,
  type UserProfileDTO,
  getDailyMessageActivity,
  type DailyActivityDataDTO,
} from "@/config/userService"
import { DailyActivityChart } from "./components/DailyActivityChart"
import { Button } from "@/components/ui/button"

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
  const [activityData, setActivityData] = useState<DailyActivityDataDTO[]>([])
  const [activityLoading, setActivityLoading] = useState(true)
  const [activityError, setActivityError] = useState<string | null>(null)

  // Fetch user profile data
  const fetchUserProfile = async () => {
    try {
      setStatsLoading(true)
      setStatsError(null)
      const profile = await getCurrentUserProfile()
      setUserProfile(profile)
    } catch (error) {
      console.error("Error fetching user profile:", error)
      setStatsError("Failed to load user statistics")
    } finally {
      setStatsLoading(false)
    }
  }

  useEffect(() => {
    fetchUserProfile()
  }, [])

  // Fetch daily activity data
  const fetchActivityData = async () => {
    try {
      setActivityLoading(true)
      setActivityError(null)
      const data = await getDailyMessageActivity(30) // Last 30 days
      setActivityData(data)
    } catch (error) {
      console.error("Error fetching daily activity:", error)
      setActivityError("Failed to load activity data")
    } finally {
      setActivityLoading(false)
    }
  }

  useEffect(() => {
    fetchActivityData()
  }, [])

  const retryFetch = () => {
    fetchUserProfile()
    fetchActivityData()
  }

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

  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="discord-dashboard">
      {/* Header Section */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6 }}
        className="dashboard-header"
      >
        <div className="user-info">
          <div className="user-avatar">
            <User className="h-8 w-8 text-white" />
          </div>
          <div className="user-details">
            <h1 className="user-name">Dashboard Overview</h1>
            <p className="user-handle">@/your-stats</p>
          </div>
        </div>

        <div className="date-badges">
          <div className="date-badge">
            <span className="date-label">Last Updated</span>
            <span className="date-value">{new Date().toLocaleDateString()}</span>
          </div>
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
      </motion.div>

      {/* Main Stats Grid */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.1 }}
        className="stats-container"
      >
        {/* Server Ranks Section */}
        <div className="stats-section">
          <div className="section-header">
            <MessageSquare className="h-5 w-5" />
            <h2>Message Stats</h2>
          </div>
          <div className="rank-cards">
            <AnimatePresence mode="wait">
              {statsLoading ? (
                <div className="rank-card loading">
                  <div className="rank-label loading-shimmer"></div>
                  <div className="rank-value loading-shimmer"></div>
                </div>
              ) : statsError ? (
                <div className="rank-card error">
                  <div className="rank-label">Error</div>
                  <div className="rank-value">--</div>
                </div>
              ) : (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="rank-card"
                >
                  <div className="rank-label">Total Messages</div>
                  <div className="rank-value">#{formatNumber(userProfile?.messageCount || 0)}</div>
                </motion.div>
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
              {statsLoading ? (
                <>
                  {["1d", "7d", "14d"].map((period) => (
                    <div key={period} className="time-card loading">
                      <div className="time-period loading-shimmer">{period}</div>
                      <div className="time-value loading-shimmer"></div>
                    </div>
                  ))}
                </>
              ) : statsError ? (
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
            {/* Placeholder voice activity data */}
            <div className="time-card">
              <div className="time-period">1d</div>
              <div className="time-value">
                0 <span>hours</span>
              </div>
            </div>
            <div className="time-card">
              <div className="time-period">7d</div>
              <div className="time-value">
                0 <span>hours</span>
              </div>
            </div>
            <div className="time-card">
              <div className="time-period">14d</div>
              <div className="time-value">
                0 <span>hours</span>
              </div>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Bottom Section */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.3 }}
        className="bottom-section"
      >
        {/* Activity Overview */}
        <div className="activity-overview">
          <div className="section-header">
            <BarChart3 className="h-5 w-5" />
            <h2>Activity Overview</h2>
          </div>
          <div className="activity-summary">
            <div className="activity-item">
              <Hash className="h-4 w-4" />
              <span className="activity-label">Daily Messages</span>
              <span className="activity-value">{formatNumber(userProfile?.messagesToday || 0)} messages</span>
            </div>
          </div>
        </div>

        {/* Charts Section */}
        <div className="charts-section">
          <div className="section-header">
            <BarChart3 className="h-5 w-5" />
            <h2>Charts</h2>
            <div className="chart-legend">
              <div className="legend-item">
                <div className="legend-dot message"></div>
                <span>Message</span>
              </div>
              <div className="legend-item">
                <div className="legend-dot voice"></div>
                <span>Voice</span>
              </div>
            </div>
          </div>
          <div className="chart-container">
            <DailyActivityChart data={activityData} loading={activityLoading} error={activityError} />
          </div>
        </div>
      </motion.div>

      {/* Footer */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.6, delay: 0.5 }}
        className="dashboard-footer"
      >
        <span>Dashboard Lookback: Last 30 days — Timezone: {Intl.DateTimeFormat().resolvedOptions().timeZone}</span>
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
  )
}
