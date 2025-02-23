import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/auth';
import { GameCard } from '@/components/ui/GameCard';
import '@/assets/dashboard.css';

// Import images for game cards
import valorantImage from '@/assets/images/valorant.jpg';
import valorantLogo from '@/assets/images/valorant-logo.png';

interface DashboardStats {
  totalPosts: number;
  followers: number;
  following: number;
}

interface Game {
  id: string;
  title: string;
  image: string;
  logo: string;
  alt: string;
}

/**
 * DashboardPage
 *
 * This page features a prominent greeting header,
 * refined grid layouts for game cards and statistic cards,
 * and updated styles.
 * 
 * With this update, the background is provided by the DashboardLayout,
 * which uses the same gradient theme as LoginPage.
 */
export function DashboardPage() {
  const { user, tokens } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Static array of games to be displayed
  const games: Game[] = [
    {
      id: 'valorant',
      title: 'Valorant',
      image: valorantImage,
      logo: valorantLogo,
      alt: 'Valorant game'
    }
  ];

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await fetch('/api/dashboard/stats', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${tokens?.accessToken}`
          }
        });

        if (!response.ok) {
          if (response.status === 401) {
            throw new Error('Session expired. Please log in again.');
          }
          throw new Error(`Error: ${response.statusText}`);
        }
        const data = await response.json();
        setStats(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load dashboard data');
      } finally {
        setIsLoading(false);
      }
    };

    fetchStats();
  }, [tokens]);

  if (isLoading) {
    return <div className="dashboard-loading text-white text-center mt-8">Loading dashboard...</div>;
  }

  return (
    <>
      <h1 className="dashboard-greeting animate-fadeSlideIn font-grandstander text-center text-5xl font-bold text-white mb-6">
        Welcome back, {user?.username}!
      </h1>

      <section className="games-section">
        <h2 className="games-title">Choose Your Game</h2>
        <div className="games-grid">
          {games.map((game) => (
            <GameCard
              key={game.id}
              title={game.title}
              image={game.image}
              logo={game.logo}
              alt={game.alt}
            />
          ))}
        </div>
      </section>

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