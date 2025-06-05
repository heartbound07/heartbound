import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { MessageCircle, X, Trophy, Clock, Users, Heart } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import type { PairingDTO } from '@/config/pairingService';
import '@/assets/MatchFoundModal.css';

interface MatchFoundModalProps {
  pairing?: PairingDTO;
  onClose: () => void;
}

export function MatchFoundModal({ pairing, onClose }: MatchFoundModalProps) {
  const [isVisible, setIsVisible] = useState(true);
  const [countdown, setCountdown] = useState(5);
  const [showFullMatch, setShowFullMatch] = useState(false);

  console.log('[MatchFoundModal] Rendering with pairing:', pairing);

  // Countdown timer effect
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1);
      }, 1000);
      return () => clearTimeout(timer);
    } else {
      setShowFullMatch(true);
    }
  }, [countdown]);

  const handleClose = () => {
    console.log('[MatchFoundModal] Closing modal');
    setIsVisible(false);
    setTimeout(onClose, 300); // Wait for animation to complete
  };

  if (!pairing) {
    console.log('[MatchFoundModal] No pairing data, returning null');
    return null;
  }

  console.log('[MatchFoundModal] Displaying modal for pairing ID:', pairing.id);

  return (
    <AnimatePresence>
      {isVisible && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm match-found-modal-backdrop flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.8, opacity: 0, y: 30 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.8, opacity: 0, y: 30 }}
            transition={{ type: "spring", duration: 0.6, bounce: 0.1 }}
            className="relative w-full max-w-lg"
          >
            <Card className="valorant-card overflow-hidden">
              {/* Animated Background Glow */}
              <div className="absolute inset-0 opacity-30">
                <motion.div
                  className="absolute inset-0 bg-gradient-to-br from-primary/20 via-purple-500/10 to-pink-500/20"
                  animate={{ 
                    backgroundPosition: ['0% 0%', '100% 100%', '0% 0%'],
                  }}
                  transition={{ 
                    duration: 8,
                    repeat: Infinity,
                    ease: "easeInOut"
                  }}
                />
              </div>

              <CardHeader className="relative text-center pb-4">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClose}
                  className="absolute right-2 top-2 text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-container-bg)]/50 transition-colors"
                >
                  <X className="h-4 w-4" />
                </Button>
                
                {!showFullMatch ? (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="space-y-4"
                  >
                    <CardTitle className="text-2xl font-bold text-[var(--color-text-primary)] mb-6 mt-4">
                      Match Found! 💕
                    </CardTitle>
                    
                    <div className="flex items-center justify-center gap-3 text-[var(--color-text-secondary)]">
                      <Clock className="h-5 w-5 text-primary" />
                      <span>Preparing your match details...</span>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.2 }}
                    className="space-y-4"
                  >
                    <CardTitle className="text-3xl font-bold text-[var(--color-text-primary)] mb-6 mt-4">
                      Perfect Match! 
                    </CardTitle>
                  </motion.div>
                )}
              </CardHeader>
              
              <CardContent className="space-y-6 relative">
                {!showFullMatch ? (
                  <motion.div 
                    className="text-center space-y-6"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                  >
                    {/* Countdown Circle */}
                    <div className="flex justify-center">
                      <div className="relative w-24 h-24">
                        <div className="absolute inset-0 bg-gradient-to-r from-primary to-purple-500 rounded-full flex items-center justify-center">
                          <span className="text-3xl font-bold text-white">{countdown}</span>
                        </div>
                      </div>
                    </div>
                    
                    {/* Loading Animation */}
                    <div className="flex justify-center space-x-2">
                      {[0, 1, 2].map((i) => (
                        <motion.div
                          key={i}
                          className="w-2 h-2 bg-primary rounded-full"
                          animate={{ 
                            y: [0, -10, 0],
                            opacity: [0.4, 1, 0.4]
                          }}
                          transition={{
                            duration: 1,
                            repeat: Infinity,
                            delay: i * 0.2
                          }}
                        />
                      ))}
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.6, delay: 0.2 }}
                    className="space-y-6"
                  >
                    {/* Compatibility Score */}
                    <motion.div 
                      className="text-center space-y-4"
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      transition={{ type: "spring", delay: 0.4 }}
                    >
                      <div className="flex items-center justify-center gap-3">
                        <motion.div
                          animate={{ rotate: [0, 15, -15, 0] }}
                          transition={{ duration: 2, repeat: Infinity, repeatDelay: 3 }}
                        >
                          <Trophy className="h-6 w-6 text-[var(--color-warning)]" />
                        </motion.div>
                        <Badge 
                          variant="secondary" 
                          className="text-base px-4 py-2 bg-gradient-to-r from-[var(--color-warning)]/20 to-orange-500/20 text-[var(--color-warning)] border-[var(--color-warning)]/30"
                        >
                          {pairing.compatibilityScore}% Compatibility
                        </Badge>
                      </div>
                    </motion.div>
                    
                    {/* Discord Channel Info */}
                    <motion.div 
                      className="p-6 bg-[var(--color-container-bg)]/80 rounded-xl border border-[var(--color-border)] backdrop-blur-sm"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.6 }}
                      whileHover={{ scale: 1.02 }}
                    >
                      <div className="text-center space-y-4">
                        <div className="flex items-center justify-center gap-3">
                          <motion.div
                            className="p-2 bg-primary/20 rounded-lg"
                            animate={{ scale: [1, 1.1, 1] }}
                            transition={{ duration: 2, repeat: Infinity }}
                          >
                            <MessageCircle className="h-6 w-6 text-primary" />
                          </motion.div>
                          <span className="text-[var(--color-text-primary)] font-semibold text-lg">
                            Discord Channel Ready
                          </span>
                        </div>
                        
                        <p className="text-[var(--color-text-secondary)]">
                          A private Discord channel has been created for you two to start chatting!
                        </p>
                        
                        <div className="flex items-center justify-center gap-2 p-3 bg-[var(--color-info)]/10 rounded-lg border border-[var(--color-info)]/20">
                          <Users className="h-4 w-4 text-[var(--color-info)]" />
                          <Badge variant="outline" className="border-[var(--color-info)]/30 text-[var(--color-info)]">
                            Channel #{pairing.discordChannelId}
                          </Badge>
                        </div>
                      </div>
                    </motion.div>
                    
                    {/* Action Button */}
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.8 }}
                      className="space-y-4"
                    >
                      <Button 
                        onClick={handleClose}
                        className="w-full valorant-button-primary h-12 text-base font-semibold"
                      >
                        <div className="flex items-center gap-2">
                          <Heart className="h-5 w-5" />
                          Start Chatting!
                        </div>
                      </Button>
                      
                      <p className="text-xs text-center text-[var(--color-text-tertiary)]">
                        Check your Discord server for the new private channel
                      </p>
                    </motion.div>
                  </motion.div>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
} 