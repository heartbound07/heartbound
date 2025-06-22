"use client"

import React from "react"
import { motion } from "framer-motion"
import { FaCoins, FaQuestion } from "react-icons/fa"
import type { UserProfileDTO } from "@/config/userService"
import "./LevelCard.css"

interface LevelCardProps {
  userProfile: UserProfileDTO | null
  loading: boolean
  error: string | null
}

export const LevelCard = React.memo(function LevelCard({ userProfile, loading, error }: LevelCardProps) {
  // XP calculation logic: Assume 1000 XP per level
  const XP_PER_LEVEL = 1000

  const calculateXPProgress = () => {
    if (!userProfile?.experience) return { current: 0, required: XP_PER_LEVEL, percentage: 0 }

    const currentLevelXP = userProfile.experience % XP_PER_LEVEL
    const percentage = (currentLevelXP / XP_PER_LEVEL) * 100

    return {
      current: currentLevelXP,
      required: XP_PER_LEVEL,
      percentage: Math.min(percentage, 100),
    }
  }

  const xpProgress = calculateXPProgress()

  // Format numbers for display
  const formatNumber = (num: number) => {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + "M"
    }
    if (num >= 1000) {
      return (num / 1000).toFixed(1) + "k"
    }
    return num.toString()
  }

  if (loading) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="level-card-wrapper"
      >
        <div className="level-card skeleton">
          <div className="user-header-section">
            <div className="skeleton-text skeleton-display-name"></div>
            <div className="skeleton-text skeleton-username"></div>
          </div>
          <div className="level-display-section">
            <div className="skeleton-text skeleton-level-number"></div>
            <div className="skeleton-text skeleton-level-text"></div>
          </div>
          <div className="progress-bars-section">
            <div className="progress-bar-item">
              <div className="skeleton-progress-bar"></div>
            </div>
          </div>
          <div className="stats-grid-section">
            <div className="stat-grid-item">
              <div className="skeleton-text skeleton-stat"></div>
            </div>
            <div className="stat-grid-item">
              <div className="skeleton-text skeleton-stat"></div>
            </div>
            <div className="stat-grid-item">
              <div className="skeleton-text skeleton-stat"></div>
            </div>
          </div>
        </div>
      </motion.div>
    )
  }

  if (error || !userProfile) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="level-card-wrapper"
      >
        <div className="level-card error">
          <div className="level-card-error">
            <span>Unable to load user profile</span>
          </div>
        </div>
      </motion.div>
    )
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.1 }}
      className="level-card-wrapper"
    >
      <div className="level-card">
        {/* User Header */}
        <div className="user-header-section">
          <div className="display-name">{userProfile.displayName || userProfile.username || "User"}</div>
          {userProfile.displayName && userProfile.username && <div className="username">@{userProfile.username}</div>}
        </div>

        {/* Level Display */}
        <div className="level-display-section">
          <div className="level-number">{userProfile.level || 1}</div>
          <div className="level-text">LEVEL</div>
        </div>

        {/* Progress Bars */}
        <div className="progress-bars-section">
          <div className="progress-bar-item">
            <div className="progress-bar-container">
              <div className="progress-bar-track">
                <motion.div
                  className="progress-bar-fill"
                  initial={{ width: 0 }}
                  animate={{ width: `${xpProgress.percentage}%` }}
                  transition={{ duration: 1, delay: 0.5, ease: "easeOut" }}
                />
              </div>
              <div className="progress-bar-label">
                {xpProgress.current} / {xpProgress.required}
              </div>
            </div>
          </div>
        </div>

        {/* Stats Grid */}
        <div className="stats-grid-section">
          <div className="stat-grid-item">
            <div className="stat-icon">
              <FaCoins />
            </div>
            <div className="stat-label">CRD:</div>
            <div className="stat-value">{formatNumber(userProfile.credits || 0)}</div>
          </div>
          <div className="stat-grid-item">
            <div className="stat-icon">
              <img src={userProfile.avatar || "/default-avatar.png"} alt="Avatar" className="avatar-icon" />
            </div>
            <div className="stat-label">USR:</div>
            <div className="stat-value">---</div>
          </div>
          <div className="stat-grid-item placeholder-stat">
            <div className="stat-icon">
              <FaQuestion />
            </div>
            <div className="stat-label">TBD:</div>
            <div className="stat-value">---</div>
          </div>
        </div>
      </div>
    </motion.div>
  )
})
