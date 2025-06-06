"use client"

import React, { useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/valorant/label"
import { AlertTriangle, Heart, X } from "lucide-react"
import { cn } from "@/utils/cn"

interface BreakupModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: (reason: string) => Promise<void>
  partnerName?: string
}

// Custom Textarea component following the project's pattern
const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ className, ...props }, ref) => {
  return (
    <textarea
      className={cn(
        "flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 resize-none",
        className
      )}
      ref={ref}
      {...props}
    />
  )
})
Textarea.displayName = "Textarea"

export const BreakupModal: React.FC<BreakupModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  partnerName = "your match"
}) => {
  const [reason, setReason] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async () => {
    if (!reason.trim()) return

    try {
      setIsSubmitting(true)
      await onConfirm(reason.trim())
      setReason("")
      onClose()
    } catch (error) {
      console.error("Error processing breakup:", error)
      // Error is handled by the parent component
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleClose = () => {
    if (!isSubmitting) {
      setReason("")
      onClose()
    }
  }

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
          <Card className="valorant-card border-[var(--color-error)]/30">
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-3 text-[var(--color-error)]">
                  <div className="p-2 bg-[var(--color-error)]/20 rounded-lg">
                    <AlertTriangle className="h-6 w-6" />
                  </div>
                  End Your Match?
                </CardTitle>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClose}
                  disabled={isSubmitting}
                  className="h-8 w-8 p-0 text-[var(--color-text-tertiary)] hover:text-[var(--color-text-primary)]"
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </CardHeader>
            
            <CardContent className="space-y-6">
              {/* Warning Message */}
              <div className="p-4 rounded-xl border border-[var(--color-warning)]/30 bg-[var(--color-warning)]/10">
                <div className="flex items-start gap-3">
                  <Heart className="h-5 w-5 text-[var(--color-warning)] flex-shrink-0 mt-0.5" />
                  <div className="space-y-2">
                    <p className="text-[var(--color-text-primary)] font-medium">
                      This action is permanent
                    </p>
                    <p className="text-[var(--color-text-secondary)] text-sm leading-relaxed">
                      Ending your match with {partnerName} will close your private channel and prevent you from matching again in the future.
                    </p>
                  </div>
                </div>
              </div>

              {/* Reason Input */}
              <div className="space-y-3">
                <Label htmlFor="breakup-reason" className="text-[var(--color-text-primary)] font-medium">
                  Reason for ending the match
                  <span className="text-[var(--color-error)] ml-1">*</span>
                </Label>
                <Textarea
                  id="breakup-reason"
                  placeholder="Please share why you're ending this match..."
                  value={reason}
                  onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setReason(e.target.value)}
                  disabled={isSubmitting}
                  className="min-h-[100px] bg-[var(--color-container-bg)] border-[var(--color-border)] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-error)] focus:ring-1 focus:ring-[var(--color-error)]/20"
                  maxLength={500}
                  rows={4}
                />
                <div className="flex justify-between items-center text-xs text-[var(--color-text-tertiary)]">
                  <span>Required field</span>
                  <span>{reason.length}/500</span>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3">
                <Button
                  variant="outline"
                  onClick={handleClose}
                  disabled={isSubmitting}
                  className="flex-1 border-[var(--color-border)] text-[var(--color-text-secondary)] hover:border-[var(--color-text-secondary)]/50 hover:text-[var(--color-text-primary)]"
                >
                  Cancel
                </Button>
                <Button
                  onClick={handleSubmit}
                  disabled={!reason.trim() || isSubmitting}
                  className="flex-1 bg-[var(--color-error)] hover:bg-[var(--color-error)]/80 text-white"
                >
                  {isSubmitting ? (
                    <>
                      <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin mr-2" />
                      Ending Match...
                    </>
                  ) : (
                    <>
                      <AlertTriangle className="h-4 w-4 mr-2" />
                      End Match
                    </>
                  )}
                </Button>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </AnimatePresence>
  )
} 