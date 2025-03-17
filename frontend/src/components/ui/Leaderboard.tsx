import React from 'react';
import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaCrown, FaTrophy, FaMedal } from 'react-icons/fa';
import '@/assets/leaderboard.css';

interface LeaderboardProps {
  users: UserProfileDTO[];
  isLoading?: boolean;
  error?: string | null;
  limit?: number;
  title?: string;
  showHeader?: boolean;
  compact?: boolean;
  className?: string;
}

export function Leaderboard({
  users = [],
  isLoading = false,
  error = null,
  limit,
  title = "Leaderboard",
  showHeader = true,
  compact = false,
  className = "",
}: LeaderboardProps) {
  // Sort users by credits in descending order
  const sortedUsers = [...users].sort((a, b) => (b.credits || 0) - (a.credits || 0));
  
  // Apply limit if specified
  const displayUsers = limit ? sortedUsers.slice(0, limit) : sortedUsers;

  // Get position-based styling and icon
  const getPositionDetails = (index: number) => {
    switch (index) {
      case 0:
        return {
          icon: <FaCrown size={18} className="text-yellow-400" />,
          className: "bg-yellow-500/10 border-yellow-500/20"
        };
      case 1:
        return {
          icon: <FaTrophy size={18} className="text-gray-300" />,
          className: "bg-gray-500/10 border-gray-500/20"
        };
      case 2:
        return {
          icon: <FaMedal size={18} className="text-amber-700" />,
          className: "bg-amber-700/10 border-amber-700/20"
        };
      default:
        return {
          icon: null,
          className: "bg-slate-800/50 border-white/5 hover:bg-slate-700/50"
        };
    }
  };

  return (
    <div className={`leaderboard-container ${className}`}>
      {showHeader && (
        <div className="leaderboard-header">
          <h2 className="leaderboard-title">{title}</h2>
        </div>
      )}

      {error && (
        <div className="leaderboard-error">
          <p>{error}</p>
        </div>
      )}

      {isLoading ? (
        <div className="leaderboard-loading">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="leaderboard-skeleton-row">
              <div className="leaderboard-skeleton-rank"></div>
              <div className="leaderboard-skeleton-user"></div>
              <div className="leaderboard-skeleton-credits"></div>
            </div>
          ))}
        </div>
      ) : (
        <div className={`leaderboard-table ${compact ? 'compact' : ''}`}>
          <div className="leaderboard-headers">
            <div className="leaderboard-header-rank">Rank</div>
            <div className="leaderboard-header-user">User</div>
            <div className="leaderboard-header-credits">Credits</div>
          </div>
          
          {displayUsers.length === 0 ? (
            <div className="leaderboard-empty">
              <p>No users to display</p>
            </div>
          ) : (
            displayUsers.map((user, index) => {
              const { icon, className: rowClass } = getPositionDetails(index);
              
              return (
                <div key={user.id} className={`leaderboard-row ${rowClass}`}>
                  <div className="leaderboard-rank">
                    {icon || <span>{index + 1}</span>}
                  </div>
                  <div className="leaderboard-user">
                    <img 
                      src={user.avatar || "/default-avatar.png"} 
                      alt={user.username} 
                      className="leaderboard-avatar" 
                    />
                    <div className="leaderboard-user-info">
                      <span className="leaderboard-username">
                        {user.displayName || user.username}
                      </span>
                      {!compact && user.displayName && user.username && (
                        <span className="leaderboard-handle">@{user.username}</span>
                      )}
                    </div>
                  </div>
                  <div className="leaderboard-credits">
                    <FaCoins className="text-yellow-400" />
                    <span>{user.credits || 0}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
