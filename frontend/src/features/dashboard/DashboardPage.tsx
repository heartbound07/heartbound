import { useState, useEffect } from 'react';
import { getCurrentUserProfile, UserProfileDTO } from '@/config/userService';
import '@/assets/dashboard.css';
import '@/assets/animations.css';

/**
 * DashboardPage
 *
 * This page displays user statistics including message count.
 * The background is provided by the DashboardLayout,
 * which uses the same gradient theme as LoginPage.
 */
export function DashboardPage() {
  const [error] = useState<string | null>(null);
  
  // State for user profile data
  const [userProfile, setUserProfile] = useState<UserProfileDTO | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState<string | null>(null);
  
  // Fetch user profile data
  useEffect(() => {
    const fetchUserProfile = async () => {
      try {
        setStatsLoading(true);
        setStatsError(null);
        const profile = await getCurrentUserProfile();
        setUserProfile(profile);
      } catch (error) {
        console.error('Error fetching user profile:', error);
        setStatsError('Failed to load user statistics');
      } finally {
        setStatsLoading(false);
      }
    };

    fetchUserProfile();
  }, []);

  return (
    <>
      {/* User Stats Section */}
      <section className="stats-section mb-12">
        <div className="stats-grid">
          {statsLoading ? (
            <div className="dashboard-card animate-fadeSlideIn">
              <div className="animate-pulse">
                <div className="h-4 bg-white/20 rounded mb-2 w-1/2"></div>
                <div className="h-8 bg-white/20 rounded w-1/3"></div>
              </div>
            </div>
          ) : statsError ? (
            <div className="dashboard-card animate-fadeSlideIn">
              <h3 className="card-title text-lg font-semibold mb-2 text-red-400">Error</h3>
              <p className="text-sm text-red-300">{statsError}</p>
            </div>
          ) : (
            <div className="dashboard-card animate-fadeSlideIn">
              <h3 className="card-title text-lg font-semibold mb-2">Total Messages</h3>
              <p className="card-value text-2xl">{userProfile?.messageCount?.toLocaleString() || 0}</p>
            </div>
          )}
        </div>
      </section>

      {/* Display error if any */}
      {error && (
        <div className="mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-center animate-fadeIn">
          {error}
        </div>
      )}

      {/*
      <div className="stats-grid mt-10">
        {error && <div className="dashboard-error text-red-400">{error}</div>}
        <div className="dashboard-card">
          <h3 className="card-title text-lg font-semibold mb-2">Total Posts</h3>
          <p className="card-value text-2xl">{stats?.totalPosts || 0}</p>
        </div>
        <div className="dashboard-card">
          <h3 className="card-title text-lg font-semibold mb-2">Followers</h3>
          <p className="card-value text-2xl">{stats?.followers || 0}</p>
        </div>
        <div className="dashboard-card">
          <h3 className="card-title text-lg font-semibold mb-2">Following</h3>
          <p className="card-value text-2xl">{stats?.following || 0}</p>
        </div>
      </div>
      */}
    </>
  );
} 