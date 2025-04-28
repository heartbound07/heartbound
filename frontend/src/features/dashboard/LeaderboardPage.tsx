import { useState, useEffect } from 'react';
import { UserProfileDTO, getLeaderboardUsers } from '@/config/userService';
import { Leaderboard } from '@/components/ui/leaderboard/Leaderboard';
import { UserRankCard } from '@/components/ui/leaderboard/UserRankCard';
import { useAuth } from '@/contexts/auth/useAuth';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
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
        setIsLoading(false);
      }
    };

    fetchLeaderboardData();
  }, [leaderboardType, user?.id, isAuthenticated]); // Re-fetch when these change

  return (
    <motion.div 
      className="leaderboard-page-container"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.5 }}
    >
      <motion.section 
        className="dashboard-section mb-6"
        initial={{ y: -20 }}
        animate={{ y: 0 }}
        transition={{ delay: 0.2, type: "spring" }}
      >
        <div className="section-header mb-6 text-center">
          <motion.h1 
            className="text-2xl md:text-3xl lg:text-4xl font-bold text-white font-grandstander"
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ delay: 0.3, type: "spring" }}
          >
            Leaderboard
          </motion.h1>
          
          <motion.div 
            className="flex justify-center mt-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.5 }}
          >
            <div className="inline-flex rounded-md shadow-sm" role="group">
              <motion.button
                type="button"
                className={`px-4 py-2 text-sm font-medium rounded-l-lg ${
                  leaderboardType === 'credits' 
                    ? 'bg-blue-600 text-white' 
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
                onClick={() => setLeaderboardType('credits')}
                aria-pressed={leaderboardType === 'credits'}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                Credits
              </motion.button>
              <motion.button
                type="button"
                className={`px-4 py-2 text-sm font-medium rounded-r-lg ${
                  leaderboardType === 'level' 
                    ? 'bg-blue-600 text-white' 
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
                onClick={() => setLeaderboardType('level')}
                aria-pressed={leaderboardType === 'level'}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                Levels
              </motion.button>
            </div>
          </motion.div>
        </div>
      </motion.section>

      <AnimatePresence>
        {isAuthenticated && currentUserProfile && (
          <motion.section 
            className="dashboard-section mb-6"
            key="user-card"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -30 }}
            transition={{ type: "spring" }}
          >
            <div className="max-w-2xl mx-auto">
              <UserRankCard 
                currentUser={currentUserProfile}
                leaderboardUsers={users}
                leaderboardType={leaderboardType}
              />
            </div>
          </motion.section>
        )}
      </AnimatePresence>

      <motion.section 
        className="dashboard-section"
        initial={{ opacity: 0, y: 50 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4, type: "spring" }}
      >
        <div className="leaderboard-content">
          <Leaderboard 
            users={users}
            isLoading={isLoading}
            error={error}
            showHeader={false}
            className="shadow-xl"
            limit={100}
            leaderboardType={leaderboardType}
          />
        </div>
      </motion.section>
    </motion.div>
  );
}