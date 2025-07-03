"use client"

import React from "react"
import { motion } from "framer-motion"
import { FaCoins } from "react-icons/fa"
import { MessageSquare, Volume2 } from "lucide-react"
import type { UserProfileDTO } from "@/config/userService"
import "./LevelCard.css"

interface LevelCardProps {
  userProfile: UserProfileDTO | null
  loading: boolean
  error: string | null
}

const EquippedBadges = React.memo(function EquippedBadges({ userProfile }: { userProfile: UserProfileDTO }) {
  // Since we now only support one badge, simplify the component
  if (!userProfile.equippedBadgeId || !userProfile.badgeUrl) {
    return null;
  }
  
  return (
    <div className="flex flex-row items-center gap-1 transition-all relative">
      <div className="relative">
        <div title={userProfile.badgeName || "Badge"} className="p-0 m-0 border-0 bg-transparent">
          <img 
            src={userProfile.badgeUrl} 
            alt={userProfile.badgeName || "Badge"}
            className="w-5 h-5 rounded-full object-cover"
            loading="lazy"
            decoding="async"
          />
        </div>
      </div>
    </div>
  );
});

export const LevelCard = React.memo(function LevelCard({ userProfile, loading, error }: LevelCardProps) {
  const calculateXPProgress = () => {
    if (userProfile?.experience == null || userProfile.xpForNextLevel == null) {
      return { current: 0, required: 0, percentage: 0 }
    }

    const current = userProfile.experience
    const required = userProfile.xpForNextLevel
    
    // Ensure required is not zero to avoid division by zero
    const percentage = required > 0 ? (current / required) * 100 : 0

    return {
      current,
      required,
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

  // Format voice time to readable format (same as Leaderboard)
  const formatVoiceTime = (minutes: number) => {
    if (minutes === 0) return "0m";
    if (minutes < 60) {
      return `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    if (remainingMinutes === 0) {
      return `${hours}h`;
    }
    return `${hours}h ${remainingMinutes}m`;
  };

  if (loading) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="level-card-wrapper"
      >
        <div className="level-card skeleton">
          <div className="level-display-section">
            <div className="user-info-left">
              <div className="skeleton-avatar"></div>
              <div className="user-text">
                <div className="skeleton-text skeleton-display-name"></div>
                <div className="skeleton-text skeleton-username"></div>
              </div>
            </div>
            <div className="level-info-right">
              <div className="skeleton-text skeleton-level-number"></div>
              <div className="skeleton-text skeleton-level-text"></div>
            </div>
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
        {/* Level Display with User Info */}
        <div 
          className={`level-display-section${userProfile.bannerUrl ? ' has-banner' : ''}`}
          style={userProfile.bannerUrl ? { backgroundImage: `url(${userProfile.bannerUrl})` } : undefined}
        >
          <div className="user-info-left">
            <div className="avatar">
              <img
                src={userProfile.avatar || "/default-avatar.png"}
                alt={userProfile.displayName || userProfile.username || "User"}
                loading="lazy"
                decoding="async"
              />
            </div>
            <div className="user-text">
              <div className="user-name-badges">
                <div className="display-name">{userProfile.displayName || userProfile.username || "User"}</div>
                <EquippedBadges userProfile={userProfile} />
              </div>
              {userProfile.displayName && userProfile.username && (
                <div className="username">@{userProfile.username}</div>
              )}
            </div>
          </div>
          <div className="level-info-right">
            <div className="level-number">{userProfile.level || 1}</div>
            <div className="level-text">LEVEL</div>
          </div>
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
              <MessageSquare className="w-4 h-4" />
            </div>
            <div className="stat-label">MSG:</div>
            <div className="stat-value">{formatNumber(userProfile.messageCount || 0)}</div>
          </div>
          <div className="stat-grid-item">
            <div className="stat-icon">
              <Volume2 className="w-4 h-4" />
            </div>
            <div className="stat-label">VT:</div>
            <div className="stat-value">{formatVoiceTime(userProfile.voiceTimeMinutesTotal || 0)}</div>
          </div>
        </div>
      </div>
    </motion.div>
  )
})
