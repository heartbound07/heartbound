import { useState, useEffect } from 'react';
import { UserProfileDTO, getLeaderboardUsers } from '@/config/userService';
import { Leaderboard } from '@/components/ui/leaderboard/Leaderboard';
import { UserRankCard } from '@/components/ui/leaderboard/UserRankCard';
import { useAuth } from '@/contexts/auth/useAuth';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import '@/assets/leaderboard.css';
import { motion, AnimatePresence } from 'framer-motion';

export function LeaderboardPage() {
  const [users, setUsers] = useState<UserProfileDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [leaderboardType, setLeaderboardType] = useState<'credits' | 'level'>('credits');
  const [currentUserProfile, setCurrentUserProfile] = useState<UserProfileDTO | null>(null);
  
  // Get authenticated user from auth context
  const { user, isAuthenticated } = useAuth();

  useEffect(() => {
    const fetchLeaderboardData = async () => {
      // Record start time for minimum loading duration
      const startTime = Date.now();
      const MIN_LOADING_TIME = 800; // 800ms minimum loading time
      
      try {
        setIsLoading(true);
        const leaderboardData = await getLeaderboardUsers(leaderboardType);
        setUsers(leaderboardData);
        
        // Find current user in leaderboard data if authenticated
        if (isAuthenticated && user?.id) {
          const userProfile = leaderboardData.find(profile => profile.id === user.id);
          setCurrentUserProfile(userProfile || null);
        } else {
          setCurrentUserProfile(null);
        }
        
        setError(null);
      } catch (err) {
        console.error('Error fetching leaderboard data:', err);
        setError('Failed to load leaderboard data. Please try again later.');
        setCurrentUserProfile(null);
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
  }, [leaderboardType, user?.id, isAuthenticated]);

  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      {/* Hero Section */}
      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        className="text-center mb-12"
      >
        <motion.h1 
          className="leaderboard-page-title"
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ delay: 0.3, type: "spring" }}
        >
          Leaderboard
        </motion.h1>
        
        {/* Toggle Controls */}
        <motion.div 
          className="flex justify-center mt-6"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5 }}
        >
          <div className="leaderboard-toggle-container">
            <motion.button
              type="button"
              className={`leaderboard-toggle-button ${
                leaderboardType === 'level' 
                  ? 'leaderboard-toggle-active' 
                  : 'leaderboard-toggle-inactive'
              }`}
              onClick={() => setLeaderboardType('level')}
              whileHover={{ scale: leaderboardType === 'level' ? 1 : 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              Levels
            </motion.button>
            
            <motion.button
              type="button"
              className={`leaderboard-toggle-button ${
                leaderboardType === 'credits' 
                  ? 'leaderboard-toggle-active' 
                  : 'leaderboard-toggle-inactive'
              }`}
              onClick={() => setLeaderboardType('credits')}
              whileHover={{ scale: leaderboardType === 'credits' ? 1 : 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              Credits
            </motion.button>
          </div>
        </motion.div>
      </motion.div>

      {/* Main Leaderboard */}
      <motion.div 
        className="mb-8"
        initial={{ opacity: isLoading ? 1 : 0, y: isLoading ? 0 : 50 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: isLoading ? 0 : 0.4, type: "spring" }}
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

      {/* User Rank Card */}
      <AnimatePresence>
        {isAuthenticated && currentUserProfile && (
          <motion.div 
            key="user-card"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -30 }}
            transition={{ type: "spring", delay: 0.6 }}
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