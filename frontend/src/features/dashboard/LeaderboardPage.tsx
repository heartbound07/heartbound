import { useState, useEffect } from 'react';
import { UserProfileDTO, getLeaderboardUsers } from '@/config/userService';
import { Leaderboard } from '@/components/ui/leaderboard/Leaderboard';
import { UserRankCard } from '@/components/ui/leaderboard/UserRankCard';
import { useAuth } from '@/contexts/auth/useAuth';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';
import { motion, AnimatePresence, LayoutGroup } from 'framer-motion';

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
            <div className="bg-[#1F2731]/60 backdrop-blur-sm rounded-xl p-2 shadow-lg border border-white/5">
              <div className="bg-[#1F2731] p-1 rounded-lg inline-flex">
                <motion.button
                  type="button"
                  className={`px-4 py-2 text-sm font-medium rounded-md relative z-10 inline-flex items-center ${
                    leaderboardType === 'level' 
                      ? 'bg-[#FF4655] text-white' 
                      : 'text-gray-300 hover:text-white bg-transparent hover:bg-white/5'
                  }`}
                  onClick={() => setLeaderboardType('level')}
                  aria-pressed={leaderboardType === 'level'}
                  whileHover={{ scale: leaderboardType === 'level' ? 1 : 1.05 }}
                  whileTap={{ scale: 0.95 }}
                >
                  <motion.span>
                    Levels
                  </motion.span>
                </motion.button>
                
                <motion.button
                  type="button"
                  className={`px-4 py-2 text-sm font-medium rounded-md relative z-10 inline-flex items-center ${
                    leaderboardType === 'credits' 
                      ? 'bg-[#FF4655] text-white' 
                      : 'text-gray-300 hover:text-white bg-transparent hover:bg-white/5'
                  }`}
                  onClick={() => setLeaderboardType('credits')}
                  aria-pressed={leaderboardType === 'credits'}
                  whileHover={{ scale: leaderboardType === 'credits' ? 1 : 1.05 }}
                  whileTap={{ scale: 0.95 }}
                >
                  <motion.span>
                    Credits
                  </motion.span>
                </motion.button>
              </div>
            </div>
          </motion.div>
        </div>
      </motion.section>

      <motion.section 
        className="dashboard-section mb-6"
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

      <AnimatePresence>
        {isAuthenticated && currentUserProfile && (
          <motion.section 
            className="dashboard-section"
            key="user-card"
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -30 }}
            transition={{ type: "spring", delay: 0.6 }}
          >
            <div className="flex justify-center w-full">
              <UserRankCard 
                currentUser={currentUserProfile}
                leaderboardUsers={users}
                leaderboardType={leaderboardType}
              />
            </div>
          </motion.section>
        )}
      </AnimatePresence>
    </motion.div>
  );
}