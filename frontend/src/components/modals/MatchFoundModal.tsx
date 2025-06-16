"use client"

import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { X, Trophy, Clock, Heart } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import type { PairingDTO } from "@/config/pairingService"
import "@/assets/MatchFoundModal.css"

interface MatchFoundModalProps {
  pairing?: PairingDTO
  onClose: () => void
}

export function MatchFoundModal({ pairing, onClose }: MatchFoundModalProps) {
  const [isVisible, setIsVisible] = useState(true)
  const [countdown, setCountdown] = useState(5)
  const [showFullMatch, setShowFullMatch] = useState(false)

  console.log("[MatchFoundModal] Rendering with pairing:", pairing)

  // Countdown timer effect
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1)
      }, 1000)
      return () => clearTimeout(timer)
    } else {
      setShowFullMatch(true)
    }
  }, [countdown])

  const handleClose = () => {
    console.log("[MatchFoundModal] Closing modal")
    setIsVisible(false)
    setTimeout(onClose, 300) // Wait for animation to complete
  }

  if (!pairing) {
    console.log("[MatchFoundModal] No pairing data, returning null")
    return null
  }

  console.log("[MatchFoundModal] Displaying modal for pairing ID:", pairing.id)

  return (
    <AnimatePresence>
      {isVisible && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm match-found-modal-backdrop flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.9, opacity: 0, y: 20 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.9, opacity: 0, y: 20 }}
            transition={{ type: "spring", duration: 0.4, bounce: 0.1 }}
            className="match-found-modal-container"
          >
            <Card className="match-found-modal-card">
              {/* Background Gradient - Matching Design System */}
              <div className="match-found-modal-bg-gradient" />

              <CardHeader className="match-found-modal-header">
                {/* Conditional Close Button - Only show after timer completes */}
                {showFullMatch && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleClose}
                    className="match-found-modal-close-btn"
                    aria-label="Close modal"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}

                {!showFullMatch ? (
                  <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
                    <CardTitle className="match-found-modal-title">Match Found! 💕</CardTitle>

                    <div className="match-found-modal-preparing">
                      <Clock className="h-5 w-5 text-[var(--modal-accent)]" />
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
                    <CardTitle className="match-found-modal-title-large">Perfect Match!</CardTitle>
                  </motion.div>
                )}
              </CardHeader>

              <CardContent className="match-found-modal-content">
                {!showFullMatch ? (
                  <motion.div
                    className="match-found-modal-countdown-section"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                  >
                    {/* Pure Timer Number - No Container */}
                    <motion.span
                      className="match-found-modal-timer-number"
                      initial={{ scale: 0.9, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      transition={{ delay: 0.2, type: "spring", bounce: 0.1 }}
                      aria-live="polite"
                      aria-atomic="true"
                    >
                      {countdown}
                    </motion.span>

                    <div className="match-found-modal-timer-label">Seconds Remaining</div>

                    {/* Loading Animation */}
                    <div className="match-found-modal-loading-dots">
                      {[0, 1, 2].map((i) => (
                        <motion.div
                          key={i}
                          className="match-found-modal-loading-dot"
                          animate={{
                            y: [0, -10, 0],
                            opacity: [0.4, 1, 0.4],
                          }}
                          transition={{
                            duration: 1,
                            repeat: Number.POSITIVE_INFINITY,
                            delay: i * 0.2,
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
                    className="match-found-modal-full-section"
                  >
                    {/* Compatibility Score */}
                    <motion.div
                      className="match-found-modal-compatibility"
                      initial={{ scale: 0.8 }}
                      animate={{ scale: 1 }}
                      transition={{ type: "spring", delay: 0.4 }}
                    >
                      <div className="match-found-modal-compatibility-row">
                        <motion.div
                          className="match-found-modal-trophy-icon"
                          animate={{ rotate: [0, 15, -15, 0] }}
                          transition={{ duration: 2, repeat: Number.POSITIVE_INFINITY, repeatDelay: 3 }}
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

                    {/* Action Button */}
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.6 }}
                      className="match-found-modal-action-section"
                    >
                      <Button onClick={handleClose} className="match-found-modal-action-button">
                        <div className="flex items-center gap-2">
                          <Heart className="h-5 w-5 text-[var(--modal-accent)]" />
                          Start Chatting!
                        </div>
                      </Button>

                      <p className="match-found-modal-action-description">
                        Your match is ready! Click to continue and start connecting.
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
  )
}
