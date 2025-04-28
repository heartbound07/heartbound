import { useState, useEffect } from 'react';
import { UserProfileDTO, getLeaderboardUsers } from '@/config/userService';
import { Leaderboard } from '@/components/ui/Leaderboard';
import '@/assets/dashboard.css';
import '@/assets/styles/fonts.css';

export function LeaderboardPage() {
  const [users, setUsers] = useState<UserProfileDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [leaderboardType, setLeaderboardType] = useState<'credits' | 'level'>('credits');

  useEffect(() => {
    const fetchLeaderboardData = async () => {
      try {
        setIsLoading(true);
        const leaderboardData = await getLeaderboardUsers(leaderboardType);
        setUsers(leaderboardData);
        setError(null);
      } catch (err) {
        console.error('Error fetching leaderboard data:', err);
        setError('Failed to load leaderboard data. Please try again later.');
      } finally {
        setIsLoading(false);
      }
    };

    fetchLeaderboardData();
  }, [leaderboardType]); // Re-fetch when leaderboardType changes

  return (
    <div className="leaderboard-page-container">
      <section className="dashboard-section mb-8">
        <div className="section-header mb-6 text-center">
          <h1 className="text-2xl md:text-3xl lg:text-4xl font-bold text-white font-grandstander">
            Leaderboard
          </h1>
          
          {/* Toggle buttons for leaderboard type */}
          <div className="flex justify-center mt-4">
            <div className="inline-flex rounded-md shadow-sm" role="group">
              <button
                type="button"
                className={`px-4 py-2 text-sm font-medium rounded-l-lg ${
                  leaderboardType === 'credits' 
                    ? 'bg-blue-600 text-white' 
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
                onClick={() => setLeaderboardType('credits')}
                aria-pressed={leaderboardType === 'credits'}
              >
                Credits
              </button>
              <button
                type="button"
                className={`px-4 py-2 text-sm font-medium rounded-r-lg ${
                  leaderboardType === 'level' 
                    ? 'bg-blue-600 text-white' 
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
                onClick={() => setLeaderboardType('level')}
                aria-pressed={leaderboardType === 'level'}
              >
                Levels
              </button>
            </div>
          </div>
        </div>
      </section>

      <section className="dashboard-section">
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
      </section>
    </div>
  );
}