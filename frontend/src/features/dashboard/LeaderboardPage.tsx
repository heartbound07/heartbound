import { useState, useEffect, useMemo, useCallback } from 'react';
import { UserProfileDTO, getLeaderboardUsers } from '@/config/userService';
import { Leaderboard } from '@/components/ui/leaderboard/Leaderboard';
import { UserRankCard } from '@/components/ui/leaderboard/UserRankCard';
import { useAuth } from '@/contexts/auth/useAuth';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/leaderboard.css';
import { motion, AnimatePresence } from 'framer-motion';

// Optimized animation variants defined outside component to prevent recreation
const ANIMATION_VARIANTS = {
  hero: {
    initial: { opacity: 0, y: 30 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.6, ease: "easeOut" }
  },
  title: {
    initial: { scale: 0.95, opacity: 0 },
    animate: { scale: 1, opacity: 1 },
    transition: { delay: 0.2, duration: 0.5, ease: "easeOut" }
  },
  leaderboard: {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    transition: { delay: 0.4, duration: 0.5, ease: "easeOut" }
  },
  // Static variant for when skeleton is loading - no animation
  leaderboardStatic: {
    initial: { opacity: 1, y: 0 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0 }
  },
  userCard: {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -20 },
    transition: { duration: 0.4, ease: "easeOut" }
  }
};

export function LeaderboardPage() {
  const [users, setUsers] = useState<UserProfileDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [leaderboardType, setLeaderboardType] = useState<'credits' | 'level' | 'messages'>('credits');
  const [currentUserProfile, setCurrentUserProfile] = useState<UserProfileDTO | null>(null);
  const [highlightedUserId, setHighlightedUserId] = useState<string | null>(null);
  
  // Get authenticated user from auth context
  const { user, isAuthenticated } = useAuth();

  // Handle UserRankCard click navigation
  const handleUserRankCardClick = useCallback((userData: UserProfileDTO) => {
    // Set highlighting - the Leaderboard component will handle page navigation automatically
    setHighlightedUserId(userData.id);
    
    // Clear highlight after 3 seconds
    setTimeout(() => {
      setHighlightedUserId(null);
    }, 3000);
  }, []);

  // Handle page navigation from leaderboard
  const handleGoToPage = useCallback(() => {
    // This callback is available for future use if needed
    // Page navigation is currently handled automatically by the Leaderboard component
  }, []);

  // Memoized current user profile calculation
  const memoizedCurrentUserProfile = useMemo(() => {
    if (!isAuthenticated || !user?.id || !users.length) return null;
    return users.find(profile => profile.id === user.id) || null;
  }, [isAuthenticated, user?.id, users]);

  useEffect(() => {
    const fetchLeaderboardData = async () => {
      // Record start time for minimum loading duration
      const startTime = Date.now();
      const MIN_LOADING_TIME = 800; // 800ms minimum loading time
      
      try {
        setIsLoading(true);
        const leaderboardData = await getLeaderboardUsers(leaderboardType);
        setUsers(leaderboardData);
        setError(null);
      } catch (err) {
        console.error('Error fetching leaderboard data:', err);
        setError('Failed to load leaderboard data. Please try again later.');
      } finally {
        // Calculate elapsed time and ensure minimum loading time
        const elapsedTime = Date.now() - startTime;
        
        if (elapsedTime < MIN_LOADING_TIME) {
          // Wait for remaining time to reach minimum loading duration
          setTimeout(() => {
            setIsLoading(false);
          }, MIN_LOADING_TIME - elapsedTime);
        } else {
          setIsLoading(false);
        }
      }
    };

    fetchLeaderboardData();
  }, [leaderboardType]);

  // Update current user profile when users change
  useEffect(() => {
    setCurrentUserProfile(memoizedCurrentUserProfile);
  }, [memoizedCurrentUserProfile]);

  return (
    <div className="bg-theme-gradient min-h-screen">
      <div className="container mx-auto px-4 py-8 max-w-6xl">
      {/* Hero Section */}
      <motion.div
        {...ANIMATION_VARIANTS.hero}
        className="text-center mb-12"
      >
        <motion.h1 
          className="leaderboard-page-title"
          {...ANIMATION_VARIANTS.title}
        >
          Leaderboard
        </motion.h1>
      </motion.div>

      {/* Main Leaderboard - Conditional animation based on loading state */}
      <motion.div 
        className="mb-8"
        {...(isLoading ? ANIMATION_VARIANTS.leaderboardStatic : ANIMATION_VARIANTS.leaderboard)}
      >
        <Leaderboard 
          users={users}
          isLoading={isLoading}
          error={error}
          showHeader={true}
          className="leaderboard-main-card"
          limit={100}
          leaderboardType={leaderboardType}
          onLeaderboardTypeChange={setLeaderboardType}
          itemsPerPage={9}
          highlightUserId={highlightedUserId}
          onGoToPage={handleGoToPage}
        />
      </motion.div>

      {/* User Rank Card - Optimized AnimatePresence */}
      <AnimatePresence mode="wait">
        {isAuthenticated && currentUserProfile && (
          <motion.div 
            key={`user-card-${currentUserProfile.id}`}
            {...ANIMATION_VARIANTS.userCard}
            className="flex justify-center w-full"
          >
            <UserRankCard 
              currentUser={currentUserProfile}
              leaderboardUsers={users}
              leaderboardType={leaderboardType}
              onClick={handleUserRankCardClick}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
    </div>
  );
}