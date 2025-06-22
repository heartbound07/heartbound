import React from 'react';
import { motion } from 'framer-motion';
import { FaCoins, FaStar } from 'react-icons/fa';
import { UserProfileDTO } from '@/config/userService';
import './LevelCard.css';

interface LevelCardProps {
  userProfile: UserProfileDTO | null;
  loading: boolean;
  error: string | null;
}

export const LevelCard = React.memo(function LevelCard({
  userProfile,
  loading,
  error
}: LevelCardProps) {
  // XP calculation logic: Assume 1000 XP per level
  // Progress = (current_experience % 1000) / 1000
  const XP_PER_LEVEL = 1000;
  
  const calculateXPProgress = () => {
    if (!userProfile?.experience) return { current: 0, required: XP_PER_LEVEL, percentage: 0 };
    
    const currentLevelXP = userProfile.experience % XP_PER_LEVEL;
    const percentage = (currentLevelXP / XP_PER_LEVEL) * 100;
    
    return {
      current: currentLevelXP,
      required: XP_PER_LEVEL,
      percentage: Math.min(percentage, 100)
    };
  };

  const xpProgress = calculateXPProgress();

  // Format numbers for display
  const formatNumber = (num: number) => {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + "M";
    }
    if (num >= 1000) {
      return (num / 1000).toFixed(1) + "k";
    }
    return num.toString();
  };

  // Calculate remaining XP for next level
  const remainingXP = xpProgress.required - xpProgress.current;

  if (loading) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="level-card-wrapper"
      >
        <div className="level-card skeleton">
          <div className="level-card-content">
            <div className="level-card-user">
              <div className="level-card-user-info">
                <div className="level-card-avatar skeleton-avatar"></div>
                <div className="level-card-identity">
                  <div className="skeleton-text skeleton-name"></div>
                  <div className="skeleton-text skeleton-username"></div>
                </div>
                <div className="level-display skeleton">
                  <div className="skeleton-text skeleton-level-number"></div>
                  <div className="skeleton-text skeleton-level-label"></div>
                </div>
              </div>
            </div>
            <div className="level-card-stats">
              <div className="level-card-stat-item">
                <div className="skeleton-text skeleton-stat"></div>
              </div>
            </div>
          </div>
          <div className="level-card-progress">
            <div className="level-progress-section">
              <div className="skeleton-text skeleton-level-indicator"></div>
              <div className="xp-progress-container">
                <div className="xp-progress-bar">
                  <div className="skeleton-progress-fill"></div>
                </div>
              </div>
            </div>
            <div className="skeleton-text skeleton-progress-stats"></div>
          </div>
        </div>
      </motion.div>
    );
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
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.1 }}
      className="level-card-wrapper"
    >
      <div className="level-card">
        <div className="level-card-content">
          {/* User Identity Section */}
          <div className="level-card-user">
            <div className="level-card-user-info">
              <div className="level-card-avatar">
                <img 
                  src={userProfile.avatar || "/default-avatar.png"} 
                  alt={userProfile.displayName || userProfile.username || 'User'} 
                  loading="lazy"
                  decoding="async"
                />
                <div className="avatar-status-dot"></div>
              </div>
              <div className="level-card-identity">
                <h2 className="level-card-name">
                  {userProfile.displayName || userProfile.username || 'User'}
                </h2>
                {userProfile.displayName && userProfile.username && (
                  <span className="level-card-username">@{userProfile.username}</span>
                )}
                {userProfile.pronouns && (
                  <span className="level-card-pronouns">{userProfile.pronouns}</span>
                )}
              </div>
              <div className="level-display">
                <span className="stat-level-number">{userProfile.level || 1}</span>
                <span className="stat-level-label">Level</span>
              </div>
            </div>
          </div>

          {/* Stats Section */}
          <div className="level-card-stats">
            <div className="level-card-stat-item credits">
              <div className="stat-icon">
                <FaCoins />
              </div>
              <div className="stat-content">
                <span className="stat-label">Credits</span>
                <span className="stat-value">{formatNumber(userProfile.credits || 0)}</span>
              </div>
            </div>
          </div>
        </div>

        {/* XP Progress Section */}
        <div className="level-card-progress">
          <div className="progress-header">
            <span className="progress-stats">
              XP to next level: {remainingXP}
            </span>
          </div>
          <div className="level-progress-section">
            <div className="level-indicator current-level">
              Level {userProfile.level || 1}
            </div>
            <div className="xp-progress-container">
              <div className="xp-progress-bar">
                <motion.div 
                  className="xp-progress-fill"
                  initial={{ width: 0 }}
                  animate={{ width: `${xpProgress.percentage}%` }}
                  transition={{ duration: 1, delay: 0.5, ease: "easeOut" }}
                />
              </div>
            </div>
          </div>
          <div className="progress-footer">
            <span className="progress-detail">
              {xpProgress.current} / {xpProgress.required} XP
            </span>
          </div>
        </div>
      </div>
    </motion.div>
  );
}); 