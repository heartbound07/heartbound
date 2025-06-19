import { useNavigate } from 'react-router-dom';
import { GameCard } from '@/components/ui/GameCard';
import './DiscoverPage.css';
import '@/assets/dashboard.css';
import '@/assets/animations.css';
import '@/assets/styles/fonts.css';
import { motion } from 'framer-motion';

// Import game assets
import valorantImage from '@/assets/images/valorant.jpg';
import valorantLogo from '@/assets/images/valorant-logo.png';
import fortniteImage from '@/assets/images/fortnite.jpg';
import fortniteLogo from '@/assets/images/fortnite-logo.png';
import leagueImage from '@/assets/images/league.jpg';
import leagueLogo from '@/assets/images/league-logo.jpg';
import dotaImage from '@/assets/images/dota.jpg';
import dotaLogo from '@/assets/images/dota-logo.png';

export function DiscoverPage() {
  const navigate = useNavigate();

  const games = [
    {
      title: 'VALORANT',
      image: valorantImage,
      logo: valorantLogo,
      alt: 'Valorant',
      isAvailable: true,
      path: '/valorant',
    },
    {
      title: 'Fortnite',
      image: fortniteImage,
      logo: fortniteLogo,
      alt: 'Fortnite',
      isAvailable: false,
    },
    {
      title: 'League of Legends',
      image: leagueImage,
      logo: leagueLogo,
      alt: 'League of Legends',
      isAvailable: false,
    },
    {
      title: 'DOTA 2',
      image: dotaImage,
      logo: dotaLogo,
      alt: 'DOTA 2',
      isAvailable: false,
    },
  ];

  return (
    <div className="discover-container">
      {/* Hero Section with framer motion animation */}
      <motion.div className="section-header mb-12 text-center">
        <motion.h1 
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ delay: 0.3, type: "spring" }}
          className="font-grandstander text-4xl md:text-5xl text-primary mb-6"
          style={{ 
            WebkitTextFillColor: 'unset',
            backgroundClip: 'unset',
            WebkitBackgroundClip: 'unset'
          }}
        >
          Discover Games
        </motion.h1>
        
        <motion.p 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5 }}
          className="discover-subtitle text-xl text-theme-secondary max-w-2xl mx-auto leading-relaxed"
        >
          Select a game to find party members and start playing.
        </motion.p>
      </motion.div>
      
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
        className="game-grid"
      >
        {games.map((game, index) => (
          <motion.div
            key={game.title}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.3 + index * 0.1 }}
            className="game-card-wrapper"
            onClick={() => {
              if (game.isAvailable && game.path) {
                navigate(game.path);
              }
            }}
          >
            <GameCard
              title={game.title}
              image={game.image}
              logo={game.logo}
              alt={game.alt}
              isAvailable={game.isAvailable}
              isClickable={game.isAvailable}
            />
          </motion.div>
        ))}
      </motion.div>
    </div>
  );
} 