"use client"

import React, { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/valorant/badge"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/valorant/avatar"
import { 
  X, 
  Users, 
  Clock, 
  TrendingUp, 
  MapPin, 
  Trophy, 
  User, 
  Calendar,
  RefreshCw,
  BarChart3,
  Activity,
  Timer,
  Target
} from "lucide-react"
import { QueueStatsDTO, QueueUserDetailsDTO, getQueueStatistics, getQueueUserDetails } from "@/config/pairingService"
import "@/assets/QueueStatsModal.css"

interface QueueStatsModalProps {
  isOpen: boolean
  onClose: () => void
}

export const QueueStatsModal: React.FC<QueueStatsModalProps> = ({
  isOpen,
  onClose
}) => {
  const [stats, setStats] = useState<QueueStatsDTO | null>(null)
  const [userDetails, setUserDetails] = useState<QueueUserDetailsDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'overview' | 'breakdown' | 'users'>('overview')

  // Fetch data when modal opens
  useEffect(() => {
    if (isOpen) {
      fetchQueueData()
    }
  }, [isOpen])

  const fetchQueueData = async () => {
    try {
      setLoading(true)
      setError(null)
      
      const [statsData, usersData] = await Promise.all([
        getQueueStatistics(),
        getQueueUserDetails()
      ])
      
      setStats(statsData)
      setUserDetails(usersData)
    } catch (err: any) {
      console.error('Error fetching queue data:', err)
      setError(err.message || 'Failed to load queue data')
    } finally {
      setLoading(false)
    }
  }

  const formatTime = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleString()
    } catch {
      return 'Unknown'
    }
  }

  const formatWaitTime = (minutes: number) => {
    if (minutes < 60) {
      return `${Math.round(minutes)}m`
    }
    const hours = Math.floor(minutes / 60)
    const remainingMinutes = Math.round(minutes % 60)
    return `${hours}h ${remainingMinutes}m`
  }

  const getRegionLabel = (region: string) => {
    const regionMap: Record<string, string> = {
      'NA_EAST': 'NA East',
      'NA_WEST': 'NA West',
      'EU': 'Europe',
      'LATAM': 'Latin America',
      'BR': 'Brazil',
      'KR': 'Korea',
      'AP': 'Asia Pacific'
    }
    return regionMap[region] || region
  }

  const getRankColor = (rank: string) => {
    const colorMap: Record<string, string> = {
      'IRON': 'text-gray-400',
      'BRONZE': 'text-orange-600',
      'SILVER': 'text-gray-300',
      'GOLD': 'text-yellow-400',
      'PLATINUM': 'text-cyan-400',
      'DIAMOND': 'text-blue-400',
      'ASCENDANT': 'text-green-400',
      'IMMORTAL': 'text-red-400',
      'RADIANT': 'text-yellow-200'
    }
    return colorMap[rank] || 'text-gray-400'
  }

  if (!isOpen) return null

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black/80 backdrop-blur-sm queue-stats-modal-backdrop flex items-center justify-center p-4 z-[1500]">
        <motion.div
          initial={{ scale: 0.8, opacity: 0, y: 30 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.8, opacity: 0, y: 30 }}
          className="queue-stats-modal-container"
        >
          <Card className="queue-stats-modal-card border-primary/30">
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-3 text-primary">
                  <div className="p-2 bg-primary/20 rounded-lg">
                    <BarChart3 className="h-6 w-6" />
                  </div>
                  Queue Analytics Dashboard
                </CardTitle>
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={fetchQueueData}
                    disabled={loading}
                    className="text-primary hover:text-primary/80"
                  >
                    <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={onClose}
                    className="h-8 w-8 p-0 text-[var(--color-text-tertiary)] hover:text-[var(--color-text-primary)]"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              
              {/* Tab Navigation */}
              <div className="flex gap-1 mt-4">
                {[
                  { key: 'overview', label: 'Overview', icon: Activity },
                  { key: 'breakdown', label: 'Breakdown', icon: BarChart3 },
                  { key: 'users', label: 'Queue Users', icon: Users }
                ].map(({ key, label, icon: Icon }) => (
                  <button
                    key={key}
                    onClick={() => setActiveTab(key as any)}
                    className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
                      activeTab === key
                        ? 'bg-primary/20 text-primary border border-primary/30'
                        : 'bg-theme-container text-theme-secondary hover:bg-theme-container/80'
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                    {label}
                  </button>
                ))}
              </div>
            </CardHeader>
            
            <CardContent className="max-h-[70vh] overflow-y-auto">
              {loading ? (
                <div className="text-center py-8">
                  <RefreshCw className="h-8 w-8 mx-auto mb-3 animate-spin text-primary" />
                  <p className="text-theme-secondary">Loading queue analytics...</p>
                </div>
              ) : error ? (
                <div className="text-center py-8 text-red-400">
                  <Target className="h-8 w-8 mx-auto mb-3 opacity-50" />
                  <p>{error}</p>
                  <Button 
                    onClick={fetchQueueData} 
                    variant="outline" 
                    size="sm" 
                    className="mt-3"
                  >
                    Retry
                  </Button>
                </div>
              ) : (
                <AnimatePresence mode="wait">
                  {activeTab === 'overview' && stats && (
                    <motion.div
                      key="overview"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -20 }}
                      className="space-y-6"
                    >
                      {/* Key Metrics */}
                      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                        <div className="p-4 rounded-lg bg-theme-container border-theme">
                          <div className="flex items-center gap-2 mb-2">
                            <Users className="h-4 w-4 text-primary" />
                            <span className="text-sm text-theme-secondary">Total in Queue</span>
                          </div>
                          <div className="text-2xl font-bold text-white">{stats.totalUsersInQueue}</div>
                        </div>
                        
                        <div className="p-4 rounded-lg bg-theme-container border-theme">
                          <div className="flex items-center gap-2 mb-2">
                            <Clock className="h-4 w-4 text-blue-400" />
                            <span className="text-sm text-theme-secondary">Avg Wait Time</span>
                          </div>
                          <div className="text-2xl font-bold text-white">
                            {formatWaitTime(stats.averageWaitTimeMinutes)}
                          </div>
                        </div>
                        
                        <div className="p-4 rounded-lg bg-theme-container border-theme">
                          <div className="flex items-center gap-2 mb-2">
                            <TrendingUp className="h-4 w-4 text-green-400" />
                            <span className="text-sm text-theme-secondary">Success Rate</span>
                          </div>
                          <div className="text-2xl font-bold text-white">
                            {Math.round(stats.matchSuccessRate)}%
                          </div>
                        </div>
                        
                        <div className="p-4 rounded-lg bg-theme-container border-theme">
                          <div className="flex items-center gap-2 mb-2">
                            <Trophy className="h-4 w-4 text-yellow-400" />
                            <span className="text-sm text-theme-secondary">Matches Today</span>
                          </div>
                          <div className="text-2xl font-bold text-white">{stats.totalMatchesCreatedToday}</div>
                        </div>
                      </div>

                      {/* System Status */}
                      <div className="space-y-3">
                        <h4 className="text-lg font-semibold text-white">System Status</h4>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div className="p-4 rounded-lg bg-theme-container border-theme">
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-sm text-theme-secondary">Queue Status</span>
                              <Badge 
                                variant="outline" 
                                className={stats.queueEnabled ? 'text-green-400 border-green-400' : 'text-red-400 border-red-400'}
                              >
                                {stats.queueEnabled ? 'Enabled' : 'Disabled'}
                              </Badge>
                            </div>
                            <p className="text-xs text-theme-tertiary">Last updated by {stats.lastUpdatedBy}</p>
                          </div>
                          
                          <div className="p-4 rounded-lg bg-theme-container border-theme">
                            <div className="flex items-center gap-2 mb-2">
                              <Timer className="h-4 w-4 text-primary" />
                              <span className="text-sm text-theme-secondary">Last Matchmaking</span>
                            </div>
                            <p className="text-sm text-white">{formatTime(stats.lastMatchmakingRun)}</p>
                          </div>
                        </div>
                      </div>

                      {/* Today's Activity */}
                      <div className="space-y-3">
                        <h4 className="text-lg font-semibold text-white">Today's Activity</h4>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                          <div className="p-4 rounded-lg bg-theme-container border-theme text-center">
                            <div className="text-xl font-bold text-green-400 mb-1">
                              {stats.totalMatchesCreatedToday}
                            </div>
                            <div className="text-sm text-theme-secondary">Matches Created</div>
                          </div>
                          
                          <div className="p-4 rounded-lg bg-theme-container border-theme text-center">
                            <div className="text-xl font-bold text-blue-400 mb-1">
                              {stats.totalUsersMatchedToday}
                            </div>
                            <div className="text-sm text-theme-secondary">Users Matched</div>
                          </div>
                          
                          <div className="p-4 rounded-lg bg-theme-container border-theme text-center">
                            <div className="text-xl font-bold text-primary mb-1">
                              {stats.totalUsersInQueue}
                            </div>
                            <div className="text-sm text-theme-secondary">Currently Queued</div>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  )}

                  {activeTab === 'breakdown' && stats && (
                    <motion.div
                      key="breakdown"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -20 }}
                      className="space-y-6"
                    >
                      {/* Region Breakdown */}
                      <div>
                        <h4 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <MapPin className="h-5 w-5 text-primary" />
                          By Region
                        </h4>
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                          {Object.entries(stats.queueByRegion).map(([region, count]) => (
                            <div key={region} className="p-3 rounded-lg bg-theme-container border-theme">
                              <div className="flex justify-between items-center">
                                <span className="text-sm text-theme-secondary">{getRegionLabel(region)}</span>
                                <Badge variant="outline" className="text-primary border-primary/30">
                                  {count}
                                </Badge>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Rank Breakdown */}
                      <div>
                        <h4 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <Trophy className="h-5 w-5 text-yellow-400" />
                          By Rank
                        </h4>
                        <div className="grid grid-cols-3 lg:grid-cols-5 gap-3">
                          {Object.entries(stats.queueByRank).map(([rank, count]) => (
                            <div key={rank} className="p-3 rounded-lg bg-theme-container border-theme">
                              <div className="text-center">
                                <div className={`text-sm font-medium ${getRankColor(rank)}`}>{rank}</div>
                                <div className="text-lg font-bold text-white">{count}</div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Gender Breakdown */}
                      <div>
                        <h4 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <User className="h-5 w-5 text-green-400" />
                          By Gender
                        </h4>
                        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                          {Object.entries(stats.queueByGender).map(([gender, count]) => (
                            <div key={gender} className="p-3 rounded-lg bg-theme-container border-theme">
                              <div className="flex justify-between items-center">
                                <span className="text-sm text-theme-secondary">
                                  {gender.replace('_', ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())}
                                </span>
                                <Badge variant="outline" className="text-green-400 border-green-400/30">
                                  {count}
                                </Badge>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Age Range Breakdown */}
                      <div>
                        <h4 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                          <Calendar className="h-5 w-5 text-purple-400" />
                          By Age Range
                        </h4>
                        <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
                          {Object.entries(stats.queueByAgeRange).map(([ageRange, count]) => (
                            <div key={ageRange} className="p-3 rounded-lg bg-theme-container border-theme">
                              <div className="text-center">
                                <div className="text-sm text-theme-secondary">{ageRange}</div>
                                <div className="text-lg font-bold text-white">{count}</div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </motion.div>
                  )}

                  {activeTab === 'users' && (
                    <motion.div
                      key="users"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: -20 }}
                      className="space-y-4"
                    >
                      <div className="flex items-center justify-between">
                        <h4 className="text-lg font-semibold text-white flex items-center gap-2">
                          <Users className="h-5 w-5 text-primary" />
                          Users in Queue ({userDetails.length})
                        </h4>
                      </div>
                      
                      {userDetails.length === 0 ? (
                        <div className="text-center py-8">
                          <Users className="h-12 w-12 mx-auto mb-3 text-theme-tertiary opacity-50" />
                          <p className="text-theme-secondary">No users currently in queue</p>
                        </div>
                      ) : (
                        <div className="space-y-3 max-h-96 overflow-y-auto">
                          {userDetails.map((user) => (
                            <div
                              key={user.userId}
                              className={`p-4 rounded-lg border transition-all ${
                                user.recentlyQueued 
                                  ? 'bg-primary/10 border-primary/30' 
                                  : 'bg-theme-container border-theme'
                              }`}
                            >
                              <div className="flex items-center gap-4">
                                <Avatar className="h-10 w-10">
                                  <AvatarImage src={user.avatar} alt={user.username} />
                                  <AvatarFallback className="bg-primary/20 text-primary">
                                    {user.username.charAt(0).toUpperCase()}
                                  </AvatarFallback>
                                </Avatar>
                                
                                <div className="flex-1">
                                  <div className="flex items-center gap-2 mb-1">
                                    <span className="font-medium text-white">{user.username}</span>
                                    {user.recentlyQueued && (
                                      <Badge variant="outline" className="text-primary border-primary/30 text-xs">
                                        Recently Joined
                                      </Badge>
                                    )}
                                  </div>
                                  
                                  <div className="flex items-center gap-4 text-sm text-theme-secondary">
                                    <span>Age: {user.age}</span>
                                    <span className={getRankColor(user.rank)}>{user.rank}</span>
                                    <span>{getRegionLabel(user.region)}</span>
                                  </div>
                                </div>
                                
                                <div className="text-right">
                                  <div className="text-sm font-medium text-white">
                                    Position #{user.queuePosition}
                                  </div>
                                  <div className="text-xs text-theme-secondary">
                                    Wait: {formatWaitTime(user.waitTimeMinutes)}
                                  </div>
                                  <div className="text-xs text-theme-tertiary">
                                    ETA: {formatWaitTime(user.estimatedWaitTimeMinutes)}
                                  </div>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </motion.div>
                  )}
                </AnimatePresence>
              )}
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </AnimatePresence>
  )
} 