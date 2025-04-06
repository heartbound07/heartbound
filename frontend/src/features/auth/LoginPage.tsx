import { useAuth } from '@/contexts/auth';
import { CloudBackground } from '@/components/backgrounds/CloudBackground';
import { Navigation } from '@/components/ui/Navigation';
import { DiscordLoginButton } from '@/components/ui/DiscordLoginButton';
import { motion } from 'framer-motion';
import { useEffect, useState } from 'react';

export function LoginPage() {
  const { startDiscordOAuth } = useAuth();
  const [titleComplete, setTitleComplete] = useState(false);
  
  // Animation variants for letter-by-letter animation
  const letterVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: (i: number) => ({
      opacity: 1,
      y: 0,
      transition: {
        delay: i * 0.05 + 0.25,
        duration: 0.5,
        ease: [0.22, 1, 0.36, 1]
      }
    })
  };
  
  // Animation variants for the subtitle
  const subtitleVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.25,
        ease: "easeOut"
      }
    }
  };
  
  // Trigger subtitle animation after title animation completes
  useEffect(() => {
    const timer = setTimeout(() => setTitleComplete(true), 1550);
    return () => clearTimeout(timer);
  }, []);
  
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#6B5BE6] to-[#8878f0] relative overflow-hidden">
      <Navigation className="font-grandstander" />
      <CloudBackground />
      <main className="relative z-10 flex flex-col items-center justify-center min-h-[calc(100vh-80px)] px-4 text-center">
        <h1 className="font-grandstander text-7xl font-bold text-white mb-8 tracking-wide inline-block">
          {Array.from("heartbound").map((letter, i) => (
            <motion.span
              key={i}
              custom={i}
              variants={letterVariants}
              initial="hidden"
              animate="visible"
              aria-hidden="true"
              className="inline-block"
            >
              {letter}
            </motion.span>
          ))}
          {/* Hidden but accessible text for screen readers */}
          <span className="sr-only">heartbound</span>
        </h1>
        
        <motion.p 
          className="text-2xl text-white/90 mb-12"
          variants={subtitleVariants}
          initial="hidden"
          animate={titleComplete ? "visible" : "hidden"}
        >
          find your perfect duo!
        </motion.p>
        
        <motion.div 
          className="bg-white/10 backdrop-blur-md rounded-2xl p-8 shadow-lg w-[340px] mx-auto"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ 
            opacity: 1, 
            scale: 1,
            transition: { 
              delay: 1.95,
              duration: 0.25,
              ease: [0.22, 1, 0.36, 1]
            }
          }}
        >
          <DiscordLoginButton />
        </motion.div>
      </main>
    </div>
  );
} 