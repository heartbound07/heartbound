"use client"

import { useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { X, Users, RefreshCw, LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import "@/assets/NoMatchFoundModal.css"

interface NoMatchFoundModalProps {
  onClose: () => void
  onStayInQueue: () => void
  onLeaveQueue: () => void
  totalInQueue?: number
  message?: string
}

export function NoMatchFoundModal({
  onClose,
  onStayInQueue,
  onLeaveQueue,
  totalInQueue,
  message = "No match found this round. Stay in queue for the next matchmaking cycle!",
}: NoMatchFoundModalProps) {
  const [isVisible, setIsVisible] = useState(true)

  console.log("[NoMatchFoundModal] Rendering with totalInQueue:", totalInQueue)

  const handleClose = () => {
    console.log("[NoMatchFoundModal] Closing modal")
    setIsVisible(false)
    setTimeout(onClose, 300) // Wait for animation to complete
  }

  const handleStayInQueue = () => {
    console.log("[NoMatchFoundModal] User chose to stay in queue")
    onStayInQueue()
    handleClose()
  }

  const handleLeaveQueue = () => {
    console.log("[NoMatchFoundModal] User chose to leave queue")
    onLeaveQueue()
    handleClose()
  }

  return (
    <AnimatePresence>
      {isVisible && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm no-match-modal-backdrop flex items-center justify-center p-4">
          <motion.div
            initial={{ scale: 0.9, opacity: 0, y: 20 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.9, opacity: 0, y: 20 }}
            transition={{ type: "spring", duration: 0.4, bounce: 0.1 }}
            className="no-match-modal-container"
          >
            <Card className="no-match-modal-card">
              {/* Background Gradient - Matching Queue Form Subtlety */}
              <div className="no-match-modal-bg-gradient" />

              <CardHeader className="no-match-modal-header">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClose}
                  className="no-match-modal-close-btn"
                  aria-label="Close modal"
                >
                  <X className="h-4 w-4" />
                </Button>

                <CardTitle className="no-match-modal-title-large">No Match This Round</CardTitle>
                <p className="no-match-modal-subtitle">{message}</p>
              </CardHeader>

              <CardContent className="no-match-modal-content">
                <div className="no-match-modal-full-section">
                  {/* Status Icon - Matching Queue Form Icon Treatment */}
                  <div className="no-match-modal-compatibility">
                    <div className="no-match-modal-compatibility-row">
                      {/* Remove the status icon div completely */}

                      {totalInQueue && totalInQueue > 1 && (
                        <div className="no-match-modal-queue-info">
                          <div className="flex items-center justify-center gap-2">
                            <Users className="h-4 w-4 text-[var(--modal-text-tertiary)]" />
                            <Badge variant="outline" className="valorant-badge">
                              {totalInQueue} still in queue
                            </Badge>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Action Buttons - Matching Queue Form Button Design */}
                  <div className="no-match-modal-action-section">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <Button
                        onClick={handleStayInQueue}
                        className="no-match-modal-action-button valorant-button-primary"
                      >
                        <div className="flex items-center gap-2">
                          <RefreshCw className="h-5 w-5 button-icon" />
                          Stay in Queue
                        </div>
                      </Button>

                      <Button onClick={handleLeaveQueue} variant="outline" className="no-match-modal-action-button">
                        <div className="flex items-center gap-2">
                          <LogOut className="h-5 w-5" />
                          Leave Queue
                        </div>
                      </Button>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  )
}
