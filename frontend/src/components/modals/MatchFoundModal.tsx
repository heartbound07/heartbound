import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, MessageCircle, X, Trophy, Clock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import type { PairingDTO } from '@/config/pairingService';

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
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.7, opacity: 0, y: 20 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.7, opacity: 0, y: 20 }}
            transition={{ type: "spring", duration: 0.5 }}
            className="relative"
          >
            <Card className="bg-gradient-to-br from-pink-900/90 to-purple-900/90 border-pink-500/50 max-w-md w-full">
              <CardHeader className="text-center relative">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClose}
                  className="absolute right-2 top-2 text-white hover:bg-white/10"
                >
                  <X className="h-4 w-4" />
                </Button>
                
                <motion.div
                  animate={{ 
                    rotate: [0, -10, 10, -10, 0],
                    scale: [1, 1.1, 1, 1.1, 1] 
                  }}
                  transition={{ 
                    duration: 2,
                    repeat: Infinity,
                    repeatDelay: 3
                  }}
                  className="mx-auto mb-4"
                >
                  <Heart className="h-16 w-16 text-pink-400 fill-current" />
                </motion.div>
                
                {!showFullMatch ? (
                  <>
                    <CardTitle className="text-2xl font-bold text-white mb-2">
                      You have been matched! 💕
                    </CardTitle>
                    <div className="flex items-center justify-center gap-2 text-pink-200">
                      <Clock className="h-5 w-5" />
                      <span>Please wait {countdown} seconds...</span>
                    </div>
                  </>
                ) : (
                  <>
                    <CardTitle className="text-2xl font-bold text-white mb-2">
                      Match Found! 💕
                    </CardTitle>
                    <p className="text-pink-200">
                      You've been paired with someone special!
                    </p>
                  </>
                )}
              </CardHeader>
              
              <CardContent className="space-y-4">
                {!showFullMatch ? (
                  <div className="text-center">
                    <motion.div
                      animate={{ scale: [1, 1.2, 1] }}
                      transition={{ duration: 1, repeat: Infinity }}
                      className="mx-auto w-16 h-16 bg-gradient-to-r from-pink-500 to-purple-500 rounded-full flex items-center justify-center mb-4"
                    >
                      <span className="text-2xl font-bold text-white">{countdown}</span>
                    </motion.div>
                    <p className="text-slate-300">
                      Preparing your match details...
                    </p>
                  </div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.5 }}
                    className="space-y-4"
                  >
                    <div className="text-center space-y-3">
                      <div className="flex items-center justify-center gap-2">
                        <Trophy className="h-5 w-5 text-yellow-400" />
                        <Badge variant="secondary" className="bg-gradient-to-r from-yellow-500/20 to-orange-500/20 text-yellow-200 border-yellow-500/30">
                          {pairing.compatibilityScore}% Compatibility
                        </Badge>
                      </div>
                      
                      <div className="p-4 bg-slate-800/50 rounded-lg border border-slate-600">
                        <div className="flex items-center justify-center gap-2 mb-2">
                          <MessageCircle className="h-5 w-5 text-blue-400" />
                          <span className="text-white font-medium">Discord Channel</span>
                        </div>
                        <p className="text-slate-300 text-sm">
                          A private Discord channel has been created for you two!
                        </p>
                        <Badge variant="outline" className="mt-2">
                          Channel ID: {pairing.discordChannelId}
                        </Badge>
                      </div>
                    </div>
                    
                    <div className="space-y-2">
                      <Button 
                        onClick={handleClose}
                        className="w-full bg-gradient-to-r from-pink-600 to-purple-600 hover:from-pink-700 hover:to-purple-700 text-white"
                      >
                        Start Chatting! 💬
                      </Button>
                      
                      <p className="text-xs text-center text-slate-400">
                        Check your Discord server for the new channel
                      </p>
                    </div>
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