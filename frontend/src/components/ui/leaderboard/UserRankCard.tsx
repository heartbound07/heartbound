import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaStar, FaTrophy, FaCrown, FaMedal } from 'react-icons/fa';
import { useTheme } from '@/contexts/ThemeContext';
import { motion } from 'framer-motion';

interface UserRankCardProps {
  currentUser: UserProfileDTO | null;
  leaderboardUsers: UserProfileDTO[];
  leaderboardType: 'credits' | 'level';
}

export function UserRankCard({ 
  currentUser, 
  leaderboardUsers, 
  leaderboardType 
}: UserRankCardProps) {
  const { theme } = useTheme();
  
  if (!currentUser) return null;

  // Find user's position in leaderboard
  const userRank = leaderboardUsers.findIndex(user => user.id === currentUser.id) + 1;
  
  if (userRank === 0) return null; // User not found in leaderboard

  // Get trophy icon based on rank
  const getRankIcon = (rank: number) => {
    switch (rank) {
      case 1:
        return <FaCrown size={24} className="text-yellow-400" />;
      case 2:
        return <FaTrophy size={22} className="text-gray-300" />;
      case 3:
        return <FaMedal size={22} className="text-amber-700" />;
      default:
        return null;
    }
  };

  const rankIcon = getRankIcon(userRank);
  
  // Apply theme-specific color for level icon
  const levelIconColor = theme === 'default' ? "text-blue-400" : "text-[var(--color-primary)]";
  
  return (
    <motion.div 
      className={`leaderboard-container mb-6 theme-transition ${theme === 'default' ? 'theme-default' : 'theme-dark'}`}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
    >
      <div className="p-4">
        <div className="flex items-center justify-center">
          <div className="flex items-center max-w-md">
            <motion.div 
              className="relative mr-4"
              initial={{ scale: 0.8 }}
              animate={{ scale: 1 }}
              transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
            >
              <img 
                src={currentUser.avatar || "/default-avatar.png"} 
                alt={currentUser.displayName || currentUser.username || "User"}
                className="w-20 h-20 rounded-full object-cover border-2 border-[var(--color-primary)]"
              />
              {rankIcon && (
                <motion.div 
                  className="absolute -bottom-2 -right-2"
                  initial={{ scale: 0, rotate: -45 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ delay: 0.4, type: "spring" }}
                >
                  {rankIcon}
                </motion.div>
              )}
            </motion.div>
            
            <div className="flex flex-col">
              <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">
                {currentUser.displayName || currentUser.username}
              </h3>
              
              <div className="flex items-center mt-1">
                {leaderboardType === 'credits' ? (
                  <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 text-sm">
                    <FaCoins className="text-yellow-400 mr-1" />
                    <span className="font-bold text-[var(--color-text-primary)]">{currentUser.credits || 0}</span>
                  </div>
                ) : (
                  <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 text-sm">
                    <FaStar className={levelIconColor + " mr-1"} />
                    <span className="font-bold text-[var(--color-text-primary)]">
                      Lvl {currentUser.level || 1}
                      <span className="text-xs text-[var(--color-text-secondary)] ml-1">
                        ({currentUser.experience || 0} XP)
                      </span>
                    </span>
                  </div>
                )}
              </div>
              
              <div className="flex items-center mt-2">
                <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 text-sm">
                  <span className="text-[var(--color-text-secondary)] mr-2">Rank:</span>
                  <span className="font-bold text-[var(--color-text-primary)]"># {userRank}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
} 