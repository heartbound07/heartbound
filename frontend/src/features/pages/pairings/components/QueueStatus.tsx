"use client"

import type React from "react"
import { memo } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Clock } from 'lucide-react'
import { motion } from "framer-motion"
import { Skeleton } from "@/components/ui/SkeletonUI"
import type { QueueUpdateEvent } from "@/contexts/types/websocket"

interface QueueStatusProps {
  queueStatus: {
    inQueue: boolean
    queuePosition?: number
    totalQueueSize?: number
    estimatedWaitTime?: number
    queuedAt?: string
  }
  queueTimer: string
  isConnected: boolean
  queueUpdate: QueueUpdateEvent | null
  actionLoading: boolean
  onLeaveQueue: () => Promise<void>
}

export const QueueStatus = memo(({
  queueStatus,
  queueTimer,
  isConnected,
  queueUpdate,
  actionLoading,
  onLeaveQueue
}: QueueStatusProps) => {
  if (!queueStatus.inQueue) {
    return null
  }

  return (
    <motion.div
      key="in-queue"
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 20 }}
      transition={{ duration: 0.5 }}
    >
      <Card className="queue-status-card">
        <CardContent className="p-8">
          {/* Header Section */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center gap-3 px-4 py-2 bg-status-info/10 rounded-full border border-status-info/20 mb-4">
              <Clock className="h-5 w-5 text-status-info" />
              <span className="text-status-info font-medium">Finding Your Match</span>
            </div>
            
            <h2 className="text-3xl font-bold text-white mb-2">
              You're in Queue!
            </h2>
            
            {/* Connection Status */}
            <div className="flex items-center justify-center gap-2">
              <div className={`w-2 h-2 rounded-full ${
                isConnected ? "bg-status-success" : "bg-status-error"
              }`} />
              <span className="text-sm text-theme-secondary">
                {isConnected ? "Connected" : "Reconnecting..."}
              </span>
            </div>
          </div>

          {/* Queue Information Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            {queueStatus.queuePosition && (queueUpdate?.totalQueueSize ?? queueStatus.totalQueueSize) && (
              <div className="text-center">
                <div className="text-3xl font-bold text-status-info mb-1">
                  {queueStatus.queuePosition}
                </div>
                <div className="text-sm text-theme-secondary">
                  of {queueUpdate?.totalQueueSize ?? queueStatus.totalQueueSize} in queue
                  <div className="text-xs text-theme-tertiary mt-1">
                    {queueUpdate?.totalQueueSize !== undefined ? '● Live' : '○ Cached'}
                  </div>
                </div>
              </div>
            )}
            
            {queueStatus.estimatedWaitTime && (
              <div className="text-center">
                <div className="text-3xl font-bold text-primary mb-1">
                  {queueStatus.estimatedWaitTime}m
                </div>
                <div className="text-sm text-theme-secondary">
                  estimated wait
                </div>
              </div>
            )}
            
            {queueStatus.inQueue && (
              <div className="text-center">
                <div className="text-3xl font-bold text-status-success mb-1">
                  {queueTimer}
                </div>
                <div className="text-sm text-theme-secondary">
                  in queue
                </div>
              </div>
            )}
          </div>

          {/* Action Button */}
          <div className="text-center">
            <Button
              variant="outline"
              onClick={onLeaveQueue}
              disabled={actionLoading}
              className="px-8 py-3 border-theme-tertiary/30 text-theme-secondary hover:border-status-error/50 hover:text-status-error transition-all duration-200"
            >
              {actionLoading ? (
                <Skeleton width="80px" height="16px" theme="valorant" className="mx-auto" />
              ) : null}
              Leave Queue
            </Button>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  )
})

QueueStatus.displayName = 'QueueStatus' 