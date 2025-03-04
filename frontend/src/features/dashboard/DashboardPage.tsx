import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { GameCard } from '@/components/ui/GameCard';
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
  const [shouldSlideOut, setShouldSlideOut] = useState(false);
  
  // Only show welcome header if it hasn't been seen this session.
  const [showWelcome, setShowWelcome] = useState(() => !sessionStorage.getItem('hasSeenWelcome'));

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
      title: 'Dota',
      image: dotaImage,
      logo: dotaLogo,
      alt: 'Dota game'
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

  // Trigger slide-out after 5 seconds
  useEffect(() => {
    const timer = setTimeout(() => {
      setShouldSlideOut(true);
    }, 5000);
    return () => clearTimeout(timer);
  }, []);

  // Handle animation end: if fade-out finished, remove welcome message from DOM
  const handleWelcomeAnimationEnd = (event: React.AnimationEvent<HTMLHeadingElement>) => {
    if (shouldSlideOut && event.animationName === 'fadeSlideOut') {
      sessionStorage.setItem('hasSeenWelcome', 'true');
      setShowWelcome(false);
    }
  };

  if (isLoading) {
    return (
      <div className="dashboard-loading text-white text-center mt-8">
        Loading dashboard...
      </div>
    );
  }

  return (
    <>
      {showWelcome && (
        <h1
          onAnimationEnd={handleWelcomeAnimationEnd}
          className={`dashboard-greeting ${shouldSlideOut ? "animate-fadeSlideOut" : "animate-welcomeEnhanced"} font-grandstander text-center text-5xl md:text-6xl font-bold text-white mb-8`}
          style={{ 
            textShadow: "0px 2px 8px rgba(0, 0, 0, 0.3)", 
            letterSpacing: "0.02em" 
          }}
        >
          Welcome back, {user?.username}!
        </h1>
      )}

      <section
        className="games-section"
      >
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
          {games.map((game) => (
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