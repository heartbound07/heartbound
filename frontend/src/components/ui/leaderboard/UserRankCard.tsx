import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaStar, FaTrophy, FaCrown, FaMedal } from 'react-icons/fa';
import { useTheme } from '@/contexts/ThemeContext';

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
  
  return (
    <div className="bg-[var(--color-container-bg)] border border-[var(--color-border)] hover:border-[var(--color-border-hover)] rounded-lg p-4 mb-6 shadow-lg backdrop-blur-sm theme-transition">
      <div className="flex items-center">
        <div className="relative mr-4">
          <img 
            src={currentUser.avatar || "/default-avatar.png"} 
            alt={currentUser.displayName || currentUser.username || "User"}
            className="w-16 h-16 rounded-full object-cover border-2 border-[var(--color-primary)]"
          />
          {rankIcon && (
            <div className="absolute -bottom-2 -right-2 bg-[var(--color-card-bg)] rounded-full p-1 border border-[var(--color-primary)]">
              {rankIcon}
            </div>
          )}
        </div>
        
        <div className="flex-1">
          <h3 className="text-lg font-semibold text-[var(--color-text-primary)]">
            {currentUser.displayName || currentUser.username}
          </h3>
          
          <div className="flex items-center mt-1">
            <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 text-sm">
              <span className="text-[var(--color-text-secondary)] mr-2">Rank:</span>
              <span className="font-bold text-[var(--color-text-primary)]"># {userRank}</span>
            </div>
            
            {leaderboardType === 'credits' ? (
              <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 ml-2 text-sm">
                <FaCoins className="text-yellow-400 mr-1" />
                <span className="font-bold text-[var(--color-text-primary)]">{currentUser.credits || 0}</span>
              </div>
            ) : (
              <div className="flex items-center bg-[var(--color-button-bg)] rounded-full px-3 py-1 ml-2 text-sm">
                <FaStar className="text-[var(--color-primary)] mr-1" />
                <span className="font-bold text-[var(--color-text-primary)]">
                  Lvl {currentUser.level || 1}
                  <span className="text-xs text-[var(--color-text-secondary)] ml-1">
                    ({currentUser.experience || 0} XP)
                  </span>
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
} 