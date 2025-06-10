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
  toggleContainer: {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    transition: { delay: 0.3, duration: 0.4 }
  },
  leaderboard: {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    transition: { delay: 0.4, duration: 0.5, ease: "easeOut" }
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
  const [leaderboardType, setLeaderboardType] = useState<'credits' | 'level'>('credits');
  const [currentUserProfile, setCurrentUserProfile] = useState<UserProfileDTO | null>(null);
  
  // Get authenticated user from auth context
  const { user, isAuthenticated } = useAuth();

  // Memoized toggle handlers to prevent unnecessary re-renders
  const handleLevelToggle = useCallback(() => {
    setLeaderboardType('level');
  }, []);

  const handleCreditsToggle = useCallback(() => {
    setLeaderboardType('credits');
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
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      {/* Hero Section - Reduced motion complexity */}
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
        
        {/* Toggle Controls - Simplified animations */}
        <motion.div 
          className="flex justify-center mt-6"
          {...ANIMATION_VARIANTS.toggleContainer}
        >
          <div className="leaderboard-toggle-container">
            <button
              type="button"
              className={`leaderboard-toggle-button ${
                leaderboardType === 'level' 
                  ? 'leaderboard-toggle-active' 
                  : 'leaderboard-toggle-inactive'
              }`}
              onClick={handleLevelToggle}
            >
              Levels
            </button>
            
            <button
              type="button"
              className={`leaderboard-toggle-button ${
                leaderboardType === 'credits' 
                  ? 'leaderboard-toggle-active' 
                  : 'leaderboard-toggle-inactive'
              }`}
              onClick={handleCreditsToggle}
            >
              Credits
            </button>
          </div>
        </motion.div>
      </motion.div>

      {/* Main Leaderboard - Single animation without complex transitions */}
      <motion.div 
        className="mb-8"
        {...ANIMATION_VARIANTS.leaderboard}
      >
        <Leaderboard 
          users={users}
          isLoading={isLoading}
          error={error}
          showHeader={false}
          className="leaderboard-main-card"
          limit={100}
          leaderboardType={leaderboardType}
          itemsPerPage={9}
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
            />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}