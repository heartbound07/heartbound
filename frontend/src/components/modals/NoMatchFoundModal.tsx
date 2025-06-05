import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Heart, X, Clock, Users, RefreshCw, LogOut, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import '@/assets/NoMatchFoundModal.css';

interface NoMatchFoundModalProps {
  onClose: () => void;
  onStayInQueue: () => void;
  onLeaveQueue: () => void;
  totalInQueue?: number;
  message?: string;
}

export function NoMatchFoundModal({ 
  onClose, 
  onStayInQueue, 
  onLeaveQueue, 
  totalInQueue,
  message = "No match found this round. Stay in queue for the next matchmaking cycle!"
}: NoMatchFoundModalProps) {
  const [isVisible, setIsVisible] = useState(true);
  const [countdown, setCountdown] = useState(5);
  const [showFullContent, setShowFullContent] = useState(false);

  console.log('[NoMatchFoundModal] Rendering with totalInQueue:', totalInQueue);

  // Countdown timer effect
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1);
      }, 1000);
      return () => clearTimeout(timer);
    } else {
      setShowFullContent(true);
    }
  }, [countdown]);

  const handleClose = () => {
    console.log('[NoMatchFoundModal] Closing modal');
    setIsVisible(false);
    setTimeout(onClose, 300); // Wait for animation to complete
  };

  const handleStayInQueue = () => {
    console.log('[NoMatchFoundModal] User chose to stay in queue');
    onStayInQueue();
    handleClose();
  };

  const handleLeaveQueue = () => {
    console.log('[NoMatchFoundModal] User chose to leave queue');
    onLeaveQueue();
    handleClose();
  };

  return (
    <AnimatePresence>
      {isVisible && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm no-match-modal-backdrop flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.8, opacity: 0, y: 30 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.8, opacity: 0, y: 30 }}
            transition={{ type: "spring", duration: 0.6, bounce: 0.1 }}
            className="no-match-modal-container"
          >
            <Card className="no-match-modal-card">
              {/* Animated Background Gradient */}
              <div className="no-match-modal-bg-gradient" />

              <CardHeader className="no-match-modal-header">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClose}
                  className="no-match-modal-close-btn"
                >
                  <X className="h-4 w-4" />
                </Button>
                
                {!showFullContent ? (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="no-match-modal-countdown-section"
                  >
                    <CardTitle className="no-match-modal-title">
                      Matchmaking Update
                    </CardTitle>
                    
                    <div className="no-match-modal-preparing">
                      <Clock className="h-5 w-5 text-[var(--color-info)]" />
                      <span>Processing results...</span>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.2 }}
                  >
                    <CardTitle className="no-match-modal-title-large">
                      No Match This Round
                    </CardTitle>
                  </motion.div>
                )}
              </CardHeader>
              
              <CardContent className="no-match-modal-content">
                {!showFullContent ? (
                  <motion.div 
                    className="no-match-modal-countdown-section"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                  >
                    {/* Simple Countdown Number */}
                    <div className="no-match-modal-countdown-display">
                      <span className="no-match-modal-countdown-number">{countdown}</span>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.6, delay: 0.2 }}
                    className="no-match-modal-full-section"
                  >
                    {/* Status Icon and Message */}
                    <motion.div 
                      className="no-match-modal-compatibility"
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      transition={{ type: "spring", delay: 0.4 }}
                    >
                      <div className="no-match-modal-compatibility-row">
                        <motion.div
                          className="no-match-modal-status-icon"
                          animate={{ rotate: [0, 10, -10, 0] }}
                          transition={{ duration: 3, repeat: Infinity, delay: 1 }}
                        >
                          <AlertCircle className="h-6 w-6 text-[var(--color-warning)]" />
                        </motion.div>
                        <p className="no-match-modal-subtitle">
                          {message}
                        </p>
                      </div>
                      
                      {totalInQueue && totalInQueue > 1 && (
                        <div className="no-match-modal-queue-info">
                          <div className="flex items-center justify-center gap-2">
                            <Users className="h-4 w-4 text-[var(--color-info)]" />
                            <Badge variant="outline" className="border-[var(--color-info)]/30 text-[var(--color-info)]">
                              {totalInQueue} still in queue
                            </Badge>
                          </div>
                        </div>
                      )}
                    </motion.div>
                    
                    {/* Action Buttons */}
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.6 }}
                      className="no-match-modal-action-section"
                    >
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Button 
                          onClick={handleStayInQueue}
                          className="no-match-modal-action-button valorant-button-primary"
                        >
                          <div className="flex items-center gap-2">
                            <RefreshCw className="h-5 w-5" />
                            Stay in Queue
                          </div>
                        </Button>
                        
                        <Button 
                          onClick={handleLeaveQueue}
                          variant="outline"
                          className="no-match-modal-action-button border-[var(--color-text-tertiary)]/30 text-[var(--color-text-secondary)] hover:border-[var(--color-error)]/50 hover:text-[var(--color-error)]"
                        >
                          <div className="flex items-center gap-2">
                            <LogOut className="h-5 w-5" />
                            Leave Queue
                          </div>
                        </Button>
                      </div>
                      
                      <p className="no-match-modal-action-description">
                        You can change your mind anytime by joining or leaving the queue
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