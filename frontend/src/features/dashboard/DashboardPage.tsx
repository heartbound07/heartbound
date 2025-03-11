import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { GameCard } from '@/components/ui/GameCard';
import { SkeletonGameCard } from '@/components/ui/SkeletonUI';
import '@/assets/dashboard.css';
import '@/assets/animations.css';

// Import images for game cards
import valorantImage from '@/assets/images/valorant.jpg';
import valorantLogo from '@/assets/images/valorant-logo.png';

import leagueImage from '@/assets/images/league.jpg';
import leagueLogo from '@/assets/images/league-logo.jpg';

import fortniteImage from '@/assets/images/fortnite.jpg';
import fortniteLogo from '@/assets/images/fortnite-logo.png';

import dotaImage from '@/assets/images/dota.jpg';
import dotaLogo from '@/assets/images/dota-logo.png';

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
  const navigate = useNavigate();
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
    },
    {
      id: 'league',
      title: 'League of Legends',
      image: leagueImage,
      logo: leagueLogo,
      alt: 'League of Legends game'
    },
    {
      id: 'fortnite',
      title: 'Fortnite',
      image: fortniteImage,
      logo: fortniteLogo,
      alt: 'Fortnite game'
    },
    {
      id: 'dota',
      title: 'Dota 2',
      image: dotaImage,
      logo: dotaLogo,
      alt: 'Dota 2 game'
    }
  ];

  // Simulate fetching dashboard data when the component mounts
  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        // Simulate a network delay of 1.5 seconds
        await new Promise(resolve => setTimeout(resolve, 1500));
        
        // Mock data for stats (in a real app, this would be from an API)
        setStats({
          totalPosts: 12,
          followers: 48,
          following: 36
        });
      } catch (err: any) {
        setError("Failed to load dashboard data. Please try again later.");
        console.error("Error fetching dashboard data:", err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchDashboardData();
  }, [tokens]);

  return (
    <>
      <section className="games-section">
        <h2
          className="games-title animate-fadeSlideIn mb-10"
          style={{ 
            fontFamily: "Grandstander, cursive", 
            textShadow: "0px 2px 6px rgba(0,0,0,0.4)",
            letterSpacing: "0.03em"
          }}
        >
          Choose Your Game
        </h2>
        <div className="games-grid">
          {isLoading ? (
            // Render skeleton placeholders when loading
            Array(4).fill(0).map((_, index) => (
              <div key={`skeleton-${index}`} className="flex justify-center p-3">
                <SkeletonGameCard theme="dashboard" />
              </div>
            ))
          ) : (
            // Render actual game cards once data is loaded
            games.map((game) => (
              <div
                key={game.id}
                onClick={() => {
                  if (game.id === 'valorant') {
                    navigate('/dashboard/valorant');
                  }
                }}
                className="flex justify-center p-3"
              >
                <GameCard
                  title={game.title}
                  image={game.image}
                  logo={game.logo}
                  alt={game.alt}
                />
              </div>
            ))
          )}
        </div>
      </section>

      {/* Display error if any */}
      {error && !isLoading && (
        <div className="mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400 text-center">
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