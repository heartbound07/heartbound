import React, { useMemo } from 'react';
import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaStar, FaTrophy, FaCrown, FaMedal } from 'react-icons/fa';
import { useTheme } from '@/contexts/ThemeContext';
import { motion } from 'framer-motion';

interface UserRankCardProps {
  currentUser: UserProfileDTO | null;
  leaderboardUsers: UserProfileDTO[];
  leaderboardType: 'credits' | 'level';
  onClick?: (userData: UserProfileDTO) => void;
}

// Optimized animation variants
const CARD_ANIMATION = {
  initial: { opacity: 0, y: 20 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.4, ease: "easeOut" }
};

const AVATAR_ANIMATION = {
  initial: { scale: 0.9 },
  animate: { scale: 1 },
  transition: { delay: 0.1, duration: 0.3, ease: "easeOut" }
};

const ICON_ANIMATION = {
  initial: { scale: 0, rotate: -45 },
  animate: { scale: 1, rotate: 0 },
  transition: { delay: 0.2, duration: 0.3, ease: "easeOut" }
};

// Removed HOVER_ANIMATION - now using CSS for better performance

export const UserRankCard = React.memo(function UserRankCard({ 
  currentUser, 
  leaderboardUsers, 
  leaderboardType,
  onClick
}: UserRankCardProps) {
  const { theme } = useTheme();
  
  // Memoized calculations
  const userData = useMemo(() => {
    if (!currentUser) return null;

    const userRank = leaderboardUsers.findIndex(user => user.id === currentUser.id) + 1;
    if (userRank === 0) return null;

    // Get trophy icon based on rank
    const getRankIcon = (rank: number) => {
      switch (rank) {
        case 1:
          return <FaCrown size={20} className="text-yellow-400" />;
        case 2:
          return <FaTrophy size={18} className="text-gray-300" />;
        case 3:
          return <FaMedal size={18} className="text-amber-700" />;
        default:
          return null;
      }
    };

    return {
      rank: userRank,
      rankIcon: getRankIcon(userRank)
    };
  }, [currentUser, leaderboardUsers]);

  // Memoized theme-specific styles
  const themeStyles = useMemo(() => ({
    levelIconColor: theme === 'default' ? "text-blue-400" : "text-[var(--color-primary)]",
    containerClass: `leaderboard-container theme-transition ${theme === 'default' ? 'theme-default' : 'theme-dark'} max-content rounded-xl shadow-md ${onClick ? 'cursor-pointer' : ''}`
  }), [theme, onClick]);
  
  // Handle click
  const handleClick = () => {
    if (onClick && userData && currentUser) {
      onClick(currentUser);
    }
  };

  if (!userData) return null;

  const { rank, rankIcon } = userData;
  
  return (
    <motion.div 
      className={themeStyles.containerClass}
      style={{ width: 'max-content' }}
      onClick={handleClick}
      {...CARD_ANIMATION}
    >
      <div className="p-2 px-3">
        <div className="flex items-center">
          <div className="flex items-center">
            <motion.div 
              className="relative mr-3"
              {...AVATAR_ANIMATION}
            >
              <img 
                src={currentUser!.avatar || "/default-avatar.png"} 
                alt={currentUser!.displayName || currentUser!.username || "User"}
                className="w-14 h-14 rounded-full object-cover border border-[var(--color-primary)]"
                loading="lazy"
                decoding="async"
              />
              {rankIcon && (
                <motion.div 
                  className="absolute -bottom-1 -right-1"
                  {...ICON_ANIMATION}
                >
                  {rankIcon}
                </motion.div>
              )}
            </motion.div>
            
            <div className="flex flex-col">
              <h3 className="text-sm font-semibold text-[var(--color-text-primary)]">
                {currentUser!.displayName || currentUser!.username}
              </h3>
              
              <div className="flex items-center gap-2 mt-0.5">
                <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-2 py-0.5 text-xs">
                  {leaderboardType === 'credits' ? (
                    <>
                      <FaCoins className="text-yellow-400 mr-1 text-xs" />
                      <span className="font-bold text-[var(--color-text-primary)]">{currentUser!.credits || 0}</span>
                    </>
                  ) : (
                    <>
                      <FaStar className={themeStyles.levelIconColor + " mr-1 text-xs"} />
                      <span className="font-bold text-[var(--color-text-primary)]">
                        Lvl {currentUser!.level || 1}
                        <span className="text-[10px] text-[var(--color-text-secondary)] ml-1">
                          ({currentUser!.experience || 0} XP)
                        </span>
                      </span>
                    </>
                  )}
                </div>
                
                <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-2 py-0.5 text-xs">
                  <span className="text-[var(--color-text-secondary)] mr-1">Rank:</span>
                  <span className="font-bold text-[var(--color-text-primary)]">#{rank}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}); 