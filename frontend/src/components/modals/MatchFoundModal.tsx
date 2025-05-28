import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, MessageCircle, X, Trophy } from 'lucide-react';
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

  const handleClose = () => {
    setIsVisible(false);
    setTimeout(onClose, 300); // Wait for animation to complete
  };

  if (!pairing) return null;

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
                
                <CardTitle className="text-2xl font-bold text-white mb-2">
                  Match Found! 💕
                </CardTitle>
                <p className="text-pink-200">
                  You've been paired with someone special!
                </p>
              </CardHeader>
              
              <CardContent className="space-y-4">
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
              </CardContent>
            </Card>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
} 