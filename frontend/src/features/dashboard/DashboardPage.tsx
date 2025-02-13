import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/auth';

interface DashboardStats {
  totalPosts: number;
  followers: number;
  following: number;
}

export function DashboardPage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        // Replace with actual API call
        const response = await fetch('/api/dashboard/stats');
        const data = await response.json();
        setStats(data);
      } catch (err) {
        setError('Failed to load dashboard data');
      } finally {
        setIsLoading(false);
      }
    };

    fetchStats();
  }, []);

  if (isLoading) {
    return <div className="dashboard-loading">Loading dashboard...</div>;
  }

  if (error) {
    return <div className="dashboard-error">{error}</div>;
  }

  return (
    <div className="dashboard-container">
      <h1>Welcome back, {user?.username}!</h1>
      
      <div className="stats-grid">
        <div className="dashboard-card">
          <h3>Total Posts</h3>
          <p>{stats?.totalPosts || 0}</p>
        </div>
        <div className="dashboard-card">
          <h3>Followers</h3>
          <p>{stats?.followers || 0}</p>
        </div>
        <div className="dashboard-card">
          <h3>Following</h3>
          <p>{stats?.following || 0}</p>
        </div>
      </div>
    </div>
  );
} 