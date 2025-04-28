import React, { useState, useRef } from 'react';
import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaCrown, FaTrophy, FaMedal, FaStar } from 'react-icons/fa';
import '@/assets/leaderboard.css';
import { UserProfileModal } from '@/components/UserProfileModal';
import { createPortal } from 'react-dom';

interface LeaderboardProps {
  users: UserProfileDTO[];
  isLoading?: boolean;
  error?: string | null;
  limit?: number;
  title?: string;
  showHeader?: boolean;
  compact?: boolean;
  className?: string;
  leaderboardType?: 'credits' | 'level';
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
  leaderboardType = 'credits',
}: LeaderboardProps) {
  // State for handling user profile modal
  const [selectedUser, setSelectedUser] = useState<UserProfileDTO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [clickPosition, setClickPosition] = useState<{ x: number, y: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Apply limit if specified, with a hard maximum of 500 entries
  const MAX_ENTRIES = 500;
  const effectiveLimit = limit ? Math.min(limit, MAX_ENTRIES) : MAX_ENTRIES;
  const displayUsers = users.slice(0, effectiveLimit);

  // Get position-based styling and icon
  const getPositionDetails = (index: number) => {
    switch (index) {
      case 0:
        return {
          icon: <FaCrown size={20} className="text-yellow-400 drop-shadow-glow" />,
          className: "bg-yellow-500/10 border-yellow-500/30 hover:bg-yellow-500/15"
        };
      case 1:
        return {
          icon: <FaTrophy size={18} className="text-gray-300 drop-shadow-glow" />,
          className: "bg-gray-500/10 border-gray-500/30 hover:bg-gray-500/15"
        };
      case 2:
        return {
          icon: <FaMedal size={18} className="text-amber-700 drop-shadow-glow" />,
          className: "bg-amber-700/10 border-amber-700/30 hover:bg-amber-700/15"
        };
      default:
        return {
          icon: null,
          className: "bg-slate-800/50 border-white/5 hover:bg-slate-700/50"
        };
    }
  };

  // Handler for user row clicks
  const handleUserClick = (user: UserProfileDTO, event: React.MouseEvent) => {
    setSelectedUser(user);
    setClickPosition({ x: event.clientX, y: event.clientY });
    setModalOpen(true);
  };

  return (
    <>
      <div ref={containerRef} className={`leaderboard-container ${className}`}>
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
              <div className="leaderboard-header-credits">
                {leaderboardType === 'credits' ? 'Credits' : 'Level'}
              </div>
            </div>
            
            {displayUsers.length === 0 ? (
              <div className="leaderboard-empty">
                <p>No users to display</p>
              </div>
            ) : (
              displayUsers.map((user, index) => {
                const { icon, className: rowClass } = getPositionDetails(index);
                
                return (
                  <div 
                    key={user.id} 
                    className={`leaderboard-row ${rowClass} cursor-pointer`}
                    onClick={(e) => handleUserClick(user, e)}
                  >
                    <div className="leaderboard-rank">
                      {icon ? (
                        <>
                          <span className="leaderboard-rank-number">{index + 1}</span>
                          <span className="leaderboard-rank-icon">{icon}</span>
                        </>
                      ) : (
                        <span>{index + 1}</span>
                      )}
                    </div>
                    <div className="leaderboard-user">
                      <img 
                        src={user.avatar || "/default-avatar.png"} 
                        alt={user.displayName || user.username || 'User'} 
                        className="leaderboard-avatar" 
                        loading="lazy"
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
                      {leaderboardType === 'credits' ? (
                        <>
                          <FaCoins className="text-yellow-400" />
                          <span>{user.credits || 0}</span>
                        </>
                      ) : (
                        <>
                          <FaStar className="text-blue-400" />
                          <span>
                            {user.level || 1}
                            {!compact && (
                              <span className="text-xs text-gray-400 ml-1">
                                ({user.experience || 0} XP)
                              </span>
                            )}
                          </span>
                        </>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* Render UserProfileModal using Portal to avoid containment issues */}
      {createPortal(
        <UserProfileModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          userProfile={selectedUser}
          position={clickPosition}
          containerRef={containerRef}
        />,
        document.body
      )}
    </>
  );
}