import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { UserProfileDTO, LeaderboardEntryDTO, getUserProfile } from '@/config/userService';
import { FaCoins, FaCrown, FaTrophy, FaMedal, FaStar } from 'react-icons/fa';
import { MessageSquare, Volume2 } from 'lucide-react';
import '@/assets/leaderboard.css';
import { UserProfileModal } from '@/components/modals/UserProfileModal';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { SkeletonLeaderboard } from '@/components/ui/SkeletonUI';

interface LeaderboardProps {
  users: LeaderboardEntryDTO[];
  isLoading?: boolean;
  error?: string | null;
  limit?: number;
  title?: string;
  showHeader?: boolean;
  compact?: boolean;
  className?: string;
  leaderboardType?: 'credits' | 'level' | 'messages' | 'voice';
  itemsPerPage?: number;
  highlightUserId?: string | null;
  onGoToPage?: (page: number) => void;
}

// Optimized animation variants defined outside component
const CONTAINER_VARIANTS = {
  visible: { 
    opacity: 1,
    transition: {
      staggerChildren: 0.03, // Reduced stagger for better performance
      delayChildren: 0.1
    }
  }
};

const ROW_VARIANTS = {
  hidden: { opacity: 0, y: 10 }, // Reduced y movement
  visible: { 
    opacity: 1, 
    y: 0,
    transition: {
      duration: 0.3,
      ease: "easeOut"
    }
  }
};

// Memoized components for better performance
const LeaderboardRow = React.memo(({ 
  user, 
  index, 
  actualIndex, 
  leaderboardType, 
  compact, 
  onClick,
  positionDetails,
  isHighlighted 
}: {
  user: LeaderboardEntryDTO;
  index: number;
  actualIndex: number;
  leaderboardType: 'credits' | 'level' | 'messages' | 'voice';
  compact: boolean;
  onClick: (user: LeaderboardEntryDTO, event: React.MouseEvent) => void;
  positionDetails: { icon: React.ReactNode; className: string };
  isHighlighted?: boolean;
}) => {
  const handleClick = useCallback((event: React.MouseEvent) => {
    onClick(user, event);
  }, [user, onClick]);

  const highlightClassName = isHighlighted ? "highlighted" : "";

  // Format voice time to readable format
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

  // Render the appropriate value and icon based on leaderboard type
  const renderValueColumn = () => {
    switch (leaderboardType) {
      case 'credits':
        return (
          <>
            <FaCoins className="text-yellow-400" />
            <span>{user.credits || 0}</span>
          </>
        );
      case 'level':
        return (
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
        );
      case 'messages':
        return (
          <>
            <MessageSquare className="text-green-400" />
            <span>{user.messageCount || 0}</span>
          </>
        );
      case 'voice':
        return (
          <>
            <Volume2 className="text-purple-400" />
            <span>{formatVoiceTime(user.voiceTimeMinutesTotal || 0)}</span>
          </>
        );
      default:
        return (
          <>
            <FaCoins className="text-yellow-400" />
            <span>{user.credits || 0}</span>
          </>
        );
    }
  };

  return (
    <motion.div
      key={user.id || index}
      className={`leaderboard-row ${positionDetails.className} ${highlightClassName} cursor-pointer`}
      onClick={handleClick}
      variants={ROW_VARIANTS}
      style={{ willChange: 'transform, opacity' }} // Optimize for animations
    >
      <div className="leaderboard-rank">
        {positionDetails.icon ? (
          <>
            <span className="leaderboard-rank-number">{actualIndex + 1}</span>
            <span className="leaderboard-rank-icon">{positionDetails.icon}</span>
          </>
        ) : (
          <span>{actualIndex + 1}</span>
        )}
      </div>
      <div className="leaderboard-user">
        <img 
          src={user.avatar || "/images/default-avatar.png"} 
          alt={user.displayName || user.username || 'User'} 
          className="leaderboard-avatar" 
          loading="lazy"
          decoding="async"
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
        {renderValueColumn()}
      </div>
    </motion.div>
  );
});

LeaderboardRow.displayName = 'LeaderboardRow';

const PaginationControls = React.memo(({ 
  currentPage, 
  totalPages, 
  onPrevPage, 
  onNextPage, 
  onGoToPage 
}: {
  currentPage: number;
  totalPages: number;
  onPrevPage: () => void;
  onNextPage: () => void;
  onGoToPage: (page: number) => void;
}) => {
  const paginationNumbers = useMemo(() => 
    generatePaginationNumbers(currentPage, totalPages), 
    [currentPage, totalPages]
  );

  return (
    <motion.div 
      className="leaderboard-pagination"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2, duration: 0.3 }}
    >
      <div className="leaderboard-pagination-controls">
        <button 
          onClick={onPrevPage}
          disabled={currentPage === 1}
          className="leaderboard-pagination-button"
          aria-label="Previous page"
        >
          <ChevronLeft size={18} />
        </button>
        
        <div className="leaderboard-pagination-pages">
          {paginationNumbers.map((page, index) => (
            <React.Fragment key={index}>
              {page === "..." ? (
                <span className="leaderboard-pagination-ellipsis">...</span>
              ) : (
                <button
                  onClick={() => onGoToPage(page as number)}
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
          onClick={onNextPage}
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
  );
});

PaginationControls.displayName = 'PaginationControls';

export const Leaderboard = React.memo(function Leaderboard({
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
  highlightUserId,
  onGoToPage,
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
  
  // Memoized calculations for better performance
  const limitedUsers = useMemo(() => 
    users.slice(0, effectiveLimit), 
    [users, effectiveLimit]
  );
  
  const paginationData = useMemo(() => {
    const totalPages = Math.ceil(limitedUsers.length / itemsPerPage);
    const startIndex = (currentPage - 1) * itemsPerPage;
    const displayUsers = limitedUsers.slice(startIndex, startIndex + itemsPerPage);
    return { totalPages, startIndex, displayUsers };
  }, [limitedUsers, currentPage, itemsPerPage]);

  const { totalPages, startIndex, displayUsers } = paginationData;
  
  // Memoized pagination handlers
  const handlePrevPage = useCallback(() => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1);
    }
  }, [currentPage]);
  
  const handleNextPage = useCallback(() => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1);
    }
  }, [currentPage, totalPages]);
  
  const goToPage = useCallback((page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
      onGoToPage?.(page);
    }
  }, [totalPages, onGoToPage]);

  // Memoized position details calculation
  const getPositionDetails = useCallback((index: number) => {
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
  }, []);

  // Optimized user click handler
  const handleUserClick = useCallback(async (user: LeaderboardEntryDTO, event: React.MouseEvent) => {
    setClickPosition({ x: event.clientX, y: event.clientY });
    setModalOpen(true);
    setSelectedUser(null); // Clear previous user data to show loading state in modal

    try {
      const fullProfile = await getUserProfile(user.id);
      setSelectedUser(fullProfile);
    } catch (error) {
      console.error("Failed to fetch user profile", error);
      // Optionally handle error state in modal by closing it or showing an error message
      setModalOpen(false);
    }
  }, []);

  const closeModal = useCallback(() => {
    setModalOpen(false);
  }, []);

  // Get column header text based on leaderboard type
  const getColumnHeader = () => {
    switch (leaderboardType) {
      case 'credits':
        return 'Credits';
      case 'level':
        return 'Level';
      case 'messages':
        return 'Messages';
      case 'voice':
        return 'Voice Time';
      default:
        return 'Credits';
    }
  };

  // Reset to first page when leaderboard type or users change
  useEffect(() => {
    setCurrentPage(1);
  }, [leaderboardType, users]);

  // Navigate to correct page when a user is highlighted
  useEffect(() => {
    if (highlightUserId && limitedUsers.length > 0) {
      const userIndex = limitedUsers.findIndex(user => user.id === highlightUserId);
      if (userIndex !== -1) {
        const targetPage = Math.ceil((userIndex + 1) / itemsPerPage);
        if (targetPage !== currentPage) {
          setCurrentPage(targetPage);
        }
      }
    }
  }, [highlightUserId, limitedUsers, itemsPerPage, currentPage]);

  return (
    <>
      <motion.div 
        ref={containerRef} 
        className={`leaderboard-container ${className}`}
        initial={{ opacity: 1 }}
        animate={{ opacity: 1 }}
      >
        {showHeader && (
          <motion.div 
            className="leaderboard-header"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.4 }}
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
          <SkeletonLeaderboard
            theme="dashboard"
            itemCount={itemsPerPage}
            showHeader={false}
            className="border-0 bg-transparent shadow-none rounded-none"
          />
        ) : (
          <motion.div
            initial="hidden"
            animate="visible"
            variants={CONTAINER_VARIANTS}
          >
            <div className={`leaderboard-table ${compact ? 'compact' : ''}`}>
              <div className="leaderboard-headers">
                <div className="leaderboard-header-rank">Rank</div>
                <div className="leaderboard-header-user">User</div>
                <div className="leaderboard-header-credits">
                  {getColumnHeader()}
                </div>
              </div>
              <div className="leaderboard-body">
                {displayUsers.length === 0 ? (
                  <div className="leaderboard-empty">
                    <p>No users to display</p>
                  </div>
                ) : (
                  displayUsers.map((user, index) => {
                    const actualIndex = startIndex + index;
                    const positionDetails = getPositionDetails(actualIndex);
                    const isHighlighted = highlightUserId === user.id;
                    
                    return (
                      <LeaderboardRow
                        key={user.id}
                        user={user}
                        index={index}
                        actualIndex={actualIndex}
                        leaderboardType={leaderboardType}
                        compact={compact}
                        onClick={handleUserClick}
                        positionDetails={positionDetails}
                        isHighlighted={isHighlighted}
                      />
                    );
                  })
                )}
              </div>
              
              {/* Pagination Controls */}
              {!isLoading && limitedUsers.length > 0 && totalPages > 1 && (
                <PaginationControls
                  currentPage={currentPage}
                  totalPages={totalPages}
                  onPrevPage={handlePrevPage}
                  onNextPage={handleNextPage}
                  onGoToPage={goToPage}
                />
              )}
            </div>
          </motion.div>
        )}
      </motion.div>

      {/* Render UserProfileModal using Portal to avoid containment issues */}
      {createPortal(
        <UserProfileModal
          isOpen={modalOpen}
          onClose={closeModal}
          userProfile={selectedUser}
          position={clickPosition}
          containerRef={containerRef}
        />,
        document.body
      )}
    </>
  );
});

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