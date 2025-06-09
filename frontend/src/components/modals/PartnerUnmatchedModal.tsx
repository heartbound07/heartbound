"use client"

import React from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { HeartCrack, Heart, X, RotateCcw } from "lucide-react"

interface PartnerUnmatchedModalProps {
  isOpen: boolean
  onClose: () => void
  onJoinQueue?: () => void
  partnerName?: string
}

export const PartnerUnmatchedModal: React.FC<PartnerUnmatchedModalProps> = ({
  isOpen,
  onClose,
  onJoinQueue,
  partnerName = "your match"
}) => {
  if (!isOpen) return null

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
        <motion.div
          initial={{ scale: 0.8, opacity: 0, y: 30 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.8, opacity: 0, y: 30 }}
          className="relative w-full max-w-md"
        >
          <Card className="valorant-card border-[var(--color-warning)]/30">
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-3 text-[var(--color-warning)]">
                  <div className="p-2 bg-[var(--color-warning)]/20 rounded-lg">
                    <HeartCrack className="h-6 w-6" />
                  </div>
                  Partner Unmatched
                </CardTitle>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onClose}
                  className="h-8 w-8 p-0 text-[var(--color-text-tertiary)] hover:text-[var(--color-text-primary)]"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </CardHeader>
            
            <CardContent className="space-y-6">
              {/* Main Message */}
              <div className="text-center space-y-4">
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ delay: 0.2, type: "spring", stiffness: 300 }}
                  className="w-20 h-20 mx-auto bg-[var(--color-warning)]/20 rounded-full flex items-center justify-center"
                >
                  <HeartCrack className="h-10 w-10 text-[var(--color-warning)]" />
                </motion.div>
                
                <div className="space-y-2">
                  <h3 className="text-xl font-semibold text-[var(--color-text-primary)]">
                    Your partner has unmatched with you :(
                  </h3>
                  <p className="text-[var(--color-text-secondary)] text-sm leading-relaxed">
                    {partnerName} has decided to end your match. Don't worry - there are plenty of other potential matches waiting for you!
                  </p>
                </div>
              </div>

              {/* Encouragement Box */}
              <div className="p-4 rounded-xl border border-[var(--color-primary)]/30 bg-[var(--color-primary)]/10">
                <div className="flex items-start gap-3">
                  <Heart className="h-5 w-5 text-[var(--color-primary)] flex-shrink-0 mt-0.5" />
                  <div className="space-y-2">
                    <p className="text-[var(--color-text-primary)] font-medium text-sm">
                      Don't give up!
                    </p>
                    <p className="text-[var(--color-text-secondary)] text-xs leading-relaxed">
                      Sometimes connections don't work out, and that's completely normal. 
                      You can join the queue again to find someone who's a better match for you.
                    </p>
                  </div>
                </div>
              </div>

              {/* Information Box */}
              <div className="p-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)]">
                <div className="space-y-2">
                  <p className="text-[var(--color-text-primary)] font-medium text-sm">
                    What happened?
                  </p>
                  <ul className="text-[var(--color-text-secondary)] text-xs space-y-1">
                    <li>• Your private Discord channel has been closed</li>
                    <li>• This match has been permanently ended</li>
                    <li>• You're free to join the queue for new matches</li>
                  </ul>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3">
                <Button
                  variant="outline"
                  onClick={onClose}
                  className="flex-1 border-[var(--color-border)] text-[var(--color-text-secondary)] hover:border-[var(--color-text-secondary)]/50 hover:text-[var(--color-text-primary)]"
                >
                  Close
                </Button>
                {onJoinQueue && (
                  <Button
                    onClick={() => {
                      onJoinQueue()
                      onClose()
                    }}
                    className="flex-1 bg-[var(--color-primary)] hover:bg-[var(--color-primary)]/80 text-white"
                  >
                    <RotateCcw className="h-4 w-4 mr-2" />
                    Join Queue
                  </Button>
                )}
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </AnimatePresence>
  )
} 