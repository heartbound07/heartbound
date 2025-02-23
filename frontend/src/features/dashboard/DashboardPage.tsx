import { useEffect, useState } from 'react';
import { useAuth } from '@/contexts/auth';
import { GameCard } from '@/components/ui/GameCard';

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

export function DashboardPage() {
  const { user, tokens } = useAuth();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Static array of games to be displayed as game cards
  const games: Game[] = [
    {
      id: 'valorant',
      title: 'Valorant',
      image: '/assets/images/valorant/valorant.jpg',
      logo: '/assets/images/valorant/valorant-logo.png',
      alt: 'Valorant game'
    },
    {
      id: 'league',
      title: 'League of Legends',
      image: '/images/leagueoflegends.jpg',
      logo: '/images/leagueoflegends-logo.png',
      alt: 'League of Legends game'
    },
    {
      id: 'rocketleague',
      title: 'Rocket League',
      image: '/images/rocketleague.jpg',
      logo: '/images/rocketleague-logo.png',
      alt: 'Rocket League game'
    },
    {
      id: 'dota2',
      title: 'Dota 2',
      image: '/images/dota2.jpg',
      logo: '/images/dota2-logo.png',
      alt: 'Dota 2 game'
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
            // If unauthorized, likely due to an expired session
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
    return <div className="dashboard-loading">Loading dashboard...</div>;
  }

  return (
    <>
      <h1 className="text-4xl font-grandstander text-white">
        Welcome back, {user?.username}!
      </h1>

      <section className="games-section">
        <h2 className="games-title">Featured Games</h2>
        <div className="games-grid">
          {games.map((game) => (
            <GameCard
              key={game.id}
              title={game.title}
              imageSrc={game.image}
              logoSrc={game.logo}
              altText={game.alt}
            />
          ))}
        </div>
      </section>

      <div className="stats-grid mt-8">
        {error && <div className="dashboard-error">{error}</div>}
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
    </>
  );
} 