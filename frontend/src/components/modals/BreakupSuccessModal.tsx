"use client"

import React from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { CheckCircle, Heart, X } from "lucide-react"

interface BreakupSuccessModalProps {
  isOpen: boolean
  onClose: () => void
  partnerName?: string
}

export const BreakupSuccessModal: React.FC<BreakupSuccessModalProps> = ({
  isOpen,
  onClose,
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
          <Card className="valorant-card border-[var(--color-success)]/30">
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-3 text-[var(--color-success)]">
                  <div className="p-2 bg-[var(--color-success)]/20 rounded-lg">
                    <CheckCircle className="h-6 w-6" />
                  </div>
                  Successfully Unmatched
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
              {/* Success Message */}
              <div className="text-center space-y-4">
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ delay: 0.2, type: "spring", stiffness: 300 }}
                  className="w-20 h-20 mx-auto bg-[var(--color-success)]/20 rounded-full flex items-center justify-center"
                >
                  <CheckCircle className="h-10 w-10 text-[var(--color-success)]" />
                </motion.div>
                
                <div className="space-y-2">
                  <h3 className="text-xl font-semibold text-[var(--color-text-primary)]">
                    You have successfully unmatched!
                  </h3>
                  <p className="text-[var(--color-text-secondary)] text-sm leading-relaxed">
                    Your match with {partnerName} has been ended. You are now free to join the queue again for new matches.
                  </p>
                </div>
              </div>

              {/* Information Box */}
              <div className="p-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-container-bg)]">
                <div className="flex items-start gap-3">
                  <Heart className="h-5 w-5 text-[var(--color-text-tertiary)] flex-shrink-0 mt-0.5" />
                  <div className="space-y-2">
                    <p className="text-[var(--color-text-primary)] font-medium text-sm">
                      What happens next?
                    </p>
                    <ul className="text-[var(--color-text-secondary)] text-xs space-y-1">
                      <li>• Your private Discord channel has been closed</li>
                      <li>• You won't be matched with this person again</li>
                      <li>• You can join the matchmaking queue for new matches</li>
                    </ul>
                  </div>
                </div>
              </div>

              {/* Action Button */}
              <div className="flex justify-center">
                <Button
                  onClick={onClose}
                  className="px-8 bg-[var(--color-success)] hover:bg-[var(--color-success)]/80 text-white"
                >
                  Continue
                </Button>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </AnimatePresence>
  )
} 