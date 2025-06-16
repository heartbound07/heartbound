"use client"

import { memo } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Clock, Wifi, WifiOff } from 'lucide-react'
import { motion } from "framer-motion"
import { Skeleton } from "@/components/ui/SkeletonUI"
import type { QueueUpdateEvent } from "@/contexts/types/websocket"
import "@/assets/QueueStatus.css"

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
    <div className="queue-status-wrapper">
      <motion.div
        key="in-queue"
        initial={{ opacity: 0, y: 20, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: -20, scale: 0.95 }}
        transition={{ 
          duration: 0.6,
          ease: [0.16, 1, 0.3, 1]
        }}
      >
        <Card className="queue-status-card">
          <CardContent className="queue-status-content">
            {/* Header Section */}
            <div className="queue-status-header">
              <div className="status-badge">
                <div className="status-badge-icon-wrapper">
                  <Clock className="status-badge-icon" />
                </div>
                <span className="status-badge-text">Finding Match</span>
              </div>
              
              <h1 className="queue-status-title">
                You're in Queue
              </h1>
              
              {/* Connection Status */}
              <div className="connection-status">
                <div className="connection-indicator">
                  {isConnected ? (
                    <Wifi className="connection-icon connected" />
                  ) : (
                    <WifiOff className="connection-icon disconnected" />
                  )}
                </div>
                <span className="connection-text">
                  {isConnected ? "Connected" : "Reconnecting..."}
                </span>
              </div>
            </div>

            {/* Queue Information Grid */}
            <div className="queue-info-grid">
              {queueStatus.queuePosition && (queueUpdate?.totalQueueSize ?? queueStatus.totalQueueSize) && (
                <div className="queue-info-item position-item">
                  <div className="queue-info-value position-value">
                    #{queueStatus.queuePosition}
                  </div>
                  <div className="queue-info-label">
                    Position in Queue
                  </div>
                  <div className="queue-info-meta">
                    {queueUpdate?.totalQueueSize ?? queueStatus.totalQueueSize} total
                    <span className="live-indicator">
                      {queueUpdate?.totalQueueSize !== undefined ? '● Live' : '○ Cached'}
                    </span>
                  </div>
                </div>
              )}
              
              {queueStatus.estimatedWaitTime && (
                <div className="queue-info-item wait-item">
                  <div className="queue-info-value wait-value">
                    {queueStatus.estimatedWaitTime}<span className="unit">min</span>
                  </div>
                  <div className="queue-info-label">
                    Estimated Wait
                  </div>
                </div>
              )}
              
              {queueStatus.inQueue && (
                <div className="queue-info-item timer-item">
                  <div className="queue-info-value timer-value">
                    {queueTimer}
                  </div>
                  <div className="queue-info-label">
                    Time Elapsed
                  </div>
                </div>
              )}
            </div>

            {/* Action Button */}
            <div className="queue-actions">
              <Button
                variant="outline"
                onClick={onLeaveQueue}
                disabled={actionLoading}
                className="leave-queue-button"
              >
                {actionLoading ? (
                  <div className="button-loading">
                    <Skeleton className="loading-skeleton" />
                    <span>Leaving...</span>
                  </div>
                ) : (
                  <span>Leave Queue</span>
                )}
              </Button>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  )
})

QueueStatus.displayName = 'QueueStatus'
