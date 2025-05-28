import React, { useState, useRef, useEffect } from 'react';
import { UserProfileDTO } from '@/config/userService';
import { FaCoins, FaCrown, FaTrophy, FaMedal, FaStar } from 'react-icons/fa';
import '@/assets/leaderboard.css';
import { UserProfileModal } from '@/components/modals/UserProfileModal';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight } from 'lucide-react';

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
  itemsPerPage?: number;
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
  itemsPerPage = 9,
}: LeaderboardProps) {
  // State for handling user profile modal
  const [selectedUser, setSelectedUser] = useState<UserProfileDTO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [clickPosition, setClickPosition] = useState<{ x: number, y: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [currentPage, setCurrentPage] = useState(1);

  // Apply limit if specified, with a hard maximum of 500 entries
  const MAX_ENTRIES = 500;
  const effectiveLimit = limit ? Math.min(limit, MAX_ENTRIES) : MAX_ENTRIES;
  const limitedUsers = users.slice(0, effectiveLimit);
  
  // Pagination calculations
  const totalPages = Math.ceil(limitedUsers.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const displayUsers = limitedUsers.slice(startIndex, startIndex + itemsPerPage);
  
  // Pagination controls
  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1);
    }
  };
  
  const handleNextPage = () => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1);
    }
  };
  
  const goToPage = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

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

  // Animation variants
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { 
      opacity: 1,
      transition: {
        when: "beforeChildren",
        staggerChildren: 0.08
      }
    }
  };
  
  const rowVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 }
  };

  // Reset to first page when leaderboard type or users change
  useEffect(() => {
    setCurrentPage(1);
  }, [leaderboardType, users]);

  return (
    <>
      <motion.div 
        ref={containerRef} 
        className={`leaderboard-container ${className}`}
        initial="hidden"
        animate="visible"
        variants={containerVariants}
      >
        {showHeader && (
          <motion.div 
            className="leaderboard-header"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5 }}
          >
            <h2 className="leaderboard-title">{title}</h2>
          </motion.div>
        )}

        {error && (
          <motion.div 
            className="leaderboard-error"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
          >
            <p>{error}</p>
          </motion.div>
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
            <div className="leaderboard-body">
              {displayUsers.length === 0 ? (
                <div className="leaderboard-empty">
                  <p>No users to display</p>
                </div>
              ) : (
                displayUsers.map((user, index) => {
                  const actualIndex = startIndex + index; // Calculate real position for proper styling
                  const { icon, className: rowClass } = getPositionDetails(actualIndex);
                  
                  return (
                    <motion.div
                      key={user.id || index}
                      className={`leaderboard-row ${rowClass} cursor-pointer`}
                      onClick={(e) => handleUserClick(user, e)}
                      variants={rowVariants}
                      whileHover={{ scale: 1.02, backgroundColor: 'rgba(255, 255, 255, 0.05)' }}
                      whileTap={{ scale: 0.98 }}
                    >
                      <div className="leaderboard-rank">
                        {icon ? (
                          <>
                            <span className="leaderboard-rank-number">{actualIndex + 1}</span>
                            <span className="leaderboard-rank-icon">{icon}</span>
                          </>
                        ) : (
                          <span>{actualIndex + 1}</span>
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
                    </motion.div>
                  );
                })
              )}
            </div>
            
            {/* Pagination Controls */}
            {!isLoading && limitedUsers.length > 0 && totalPages > 1 && (
              <motion.div 
                className="leaderboard-pagination"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3 }}
              >
                <div className="leaderboard-pagination-controls">
                  <button 
                    onClick={handlePrevPage}
                    disabled={currentPage === 1}
                    className="leaderboard-pagination-button"
                    aria-label="Previous page"
                  >
                    <ChevronLeft size={18} />
                  </button>
                  
                  <div className="leaderboard-pagination-pages">
                    {generatePaginationNumbers(currentPage, totalPages).map((page, index) => (
                      <React.Fragment key={index}>
                        {page === "..." ? (
                          <span className="leaderboard-pagination-ellipsis">...</span>
                        ) : (
                          <button
                            onClick={() => goToPage(page as number)}
                            className={`leaderboard-pagination-number ${currentPage === page ? 'active' : ''}`}
                            aria-current={currentPage === page ? 'page' : undefined}
                          >
                            {page}
                          </button>
                        )}
                      </React.Fragment>
                    ))}
                  </div>
                  
                  <button 
                    onClick={handleNextPage}
                    disabled={currentPage === totalPages}
                    className="leaderboard-pagination-button"
                    aria-label="Next page"
                  >
                    <ChevronRight size={18} />
                  </button>
                </div>
                <div className="leaderboard-pagination-info">
                  Page {currentPage} of {totalPages}
                </div>
              </motion.div>
            )}
          </div>
        )}
      </motion.div>

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

// Helper function to generate pagination numbers with ellipsis for large page counts
function generatePaginationNumbers(currentPage: number, totalPages: number) {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }
  
  if (currentPage <= 3) {
    return [1, 2, 3, 4, "...", totalPages - 1, totalPages];
  }
  
  if (currentPage >= totalPages - 2) {
    return [1, 2, "...", totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
  }
  
  return [
    1, 
    "...", 
    currentPage - 1, 
    currentPage, 
    currentPage + 1, 
    "...", 
    totalPages
  ];
}