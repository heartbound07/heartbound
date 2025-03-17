import React, { useState, useEffect } from 'react';
import { UserProfileDTO, getUserProfiles } from '@/config/userService';
import { Leaderboard } from '@/components/ui/Leaderboard';
import httpClient from '@/lib/api/httpClient';
import '@/assets/dashboard.css';

export function LeaderboardPage() {
  const [users, setUsers] = useState<UserProfileDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchLeaderboardData = async () => {
      try {
        setIsLoading(true);
        const response = await httpClient.get('/api/users/leaderboard');
        setUsers(response.data);
        setError(null);
      } catch (err) {
        console.error('Error fetching leaderboard data:', err);
        setError('Failed to load leaderboard data. Please try again later.');
      } finally {
        setIsLoading(false);
      }
    };

    fetchLeaderboardData();
  }, []);

  return (
    <>
      <section className="dashboard-section mb-8">
        <div className="section-header mb-6">
          <h1 className="text-2xl md:text-3xl font-bold text-white">
            Credits Leaderboard
          </h1>
          <p className="text-white/70 mt-2">
            See who's leading the way with the most credits in our community.
          </p>
        </div>
      </section>

      <section className="dashboard-section">
        <div className="grid grid-cols-1 gap-6">
          <Leaderboard 
            users={users}
            isLoading={isLoading}
            error={error}
            showHeader={false}
          />
        </div>
      </section>
    </>
  );
}
