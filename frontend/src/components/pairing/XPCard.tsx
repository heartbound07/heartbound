import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy, Zap, Flame, Star, TrendingUp, Award, Target, Calendar } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import { Button } from '@/components/ui/button';

// Types for XP system data
interface PairLevelData {
  id: number;
  pairingId: number;
  currentLevel: number;
  totalXP: number;
  currentLevelXP: number;
  nextLevelXP: number;
  xpNeededForNextLevel: number;
  levelProgressPercentage: number;
  readyToLevelUp: boolean;
}

interface Achievement {
  id: number;
  achievementKey: string;
  name: string;
  description: string;
  achievementType: string;
  xpReward: number;
  rarity: string;
  tier: string;
}

interface PairAchievement {
  id: number;
  achievement: Achievement;
  unlockedAt: string;
  progressValue: number;
  xpAwarded: number;
  recentlyUnlocked: boolean;
  unlockTimeDisplay: string;
}

interface VoiceStreakStats {
  currentStreak: number;
  highestStreak: number;
  totalVoiceMinutes: number;
  totalVoiceHours: number;
  totalActiveDays: number;
  hasActivityToday: boolean;
}

interface XPCardProps {
  pairingId: number;
  className?: string;
}

export const XPCard: React.FC<XPCardProps> = ({ pairingId, className = '' }) => {
  const [levelData, setLevelData] = useState<PairLevelData | null>(null);
  const [achievements, setAchievements] = useState<PairAchievement[]>([]);
  const [availableAchievements, setAvailableAchievements] = useState<Achievement[]>([]);
  const [voiceStreakStats, setVoiceStreakStats] = useState<VoiceStreakStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'overview' | 'achievements' | 'streaks'>('overview');

  // Fetch XP data
  const fetchXPData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [levelResponse, achievementsResponse, availableResponse, streaksResponse] = await Promise.allSettled([
        fetch(`/api/pairings/${pairingId}/level`, {
          headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
        }),
        fetch(`/api/pairings/${pairingId}/achievements`, {
          headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
        }),
        fetch(`/api/pairings/${pairingId}/achievements/available`, {
          headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
        }),
        fetch(`/api/pairings/${pairingId}/streaks`, {
          headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
        })
      ]);

      // Handle level data
      if (levelResponse.status === 'fulfilled' && levelResponse.value.ok) {
        const levelData = await levelResponse.value.json();
        setLevelData(levelData);
      }

      // Handle achievements
      if (achievementsResponse.status === 'fulfilled' && achievementsResponse.value.ok) {
        const achievementsData = await achievementsResponse.value.json();
        setAchievements(achievementsData);
      }

      // Handle available achievements
      if (availableResponse.status === 'fulfilled' && availableResponse.value.ok) {
        const availableData = await availableResponse.value.json();
        setAvailableAchievements(availableData);
      }

      // Handle voice streaks
      if (streaksResponse.status === 'fulfilled' && streaksResponse.value.ok) {
        const streaksData = await streaksResponse.value.json();
        setVoiceStreakStats(streaksData.statistics);
      }

    } catch (err) {
      console.error('Error fetching XP data:', err);
      setError('Failed to load XP data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (pairingId) {
      fetchXPData();
    }
  }, [pairingId]);

  // Get rarity color
  const getRarityColor = (rarity: string) => {
    switch (rarity.toLowerCase()) {
      case 'legendary': return 'text-yellow-400 border-yellow-400';
      case 'epic': return 'text-purple-400 border-purple-400';
      case 'rare': return 'text-blue-400 border-blue-400';
      case 'common': return 'text-green-400 border-green-400';
      default: return 'text-gray-400 border-gray-400';
    }
  };

  // Get streak tier color
  const getStreakTierColor = (tier: string) => {
    switch (tier.toLowerCase()) {
      case 'legendary': return 'text-yellow-400';
      case 'epic': return 'text-purple-400';
      case 'rare': return 'text-blue-400';
      case 'common': return 'text-green-400';
      default: return 'text-gray-400';
    }
  };

  if (loading) {
    return (
      <Card className={`valorant-card ${className}`}>
        <CardHeader className="pb-4">
          <CardTitle className="flex items-center gap-3 text-white text-xl">
            <div className="p-2 bg-primary/20 rounded-lg">
              <Zap className="h-5 w-5 text-primary animate-pulse" />
            </div>
            Pair Level & Achievements
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="h-20 bg-[var(--color-container-bg)] rounded-lg animate-pulse" />
            <div className="h-16 bg-[var(--color-container-bg)] rounded-lg animate-pulse" />
            <div className="h-12 bg-[var(--color-container-bg)] rounded-lg animate-pulse" />
          </div>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className={`valorant-card ${className}`}>
        <CardContent className="p-6">
          <div className="text-center text-red-400">
            <Trophy className="h-8 w-8 mx-auto mb-2 opacity-50" />
            <p>{error}</p>
            <Button 
              onClick={fetchXPData} 
              variant="outline" 
              size="sm" 
              className="mt-2"
            >
              Retry
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className={`valorant-card ${className}`}>
      <CardHeader className="pb-4">
        <CardTitle className="flex items-center gap-3 text-white text-xl">
          <div className="p-2 bg-primary/20 rounded-lg">
            <Zap className="h-5 w-5 text-primary" />
          </div>
          Pair Level & Achievements
        </CardTitle>
        
        {/* Tab Navigation */}
        <div className="flex gap-2 mt-4">
          {[
            { key: 'overview', label: 'Overview', icon: TrendingUp },
            { key: 'achievements', label: 'Achievements', icon: Trophy },
            { key: 'streaks', label: 'Streaks', icon: Flame }
          ].map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setActiveTab(key as any)}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                activeTab === key
                  ? 'bg-primary/20 text-primary border border-primary/30'
                  : 'bg-[var(--color-container-bg)] text-[var(--color-text-secondary)] hover:bg-[var(--color-container-bg)]/80'
              }`}
            >
              <Icon className="h-4 w-4" />
              {label}
            </button>
          ))}
        </div>
      </CardHeader>

      <CardContent>
        <AnimatePresence mode="wait">
          {activeTab === 'overview' && (
            <motion.div
              key="overview"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
              className="space-y-6"
            >
              {/* Level Progress */}
              {levelData && (
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="p-3 bg-gradient-to-br from-primary/20 to-primary/10 rounded-xl">
                        <Star className="h-6 w-6 text-primary" />
                      </div>
                      <div>
                        <h3 className="text-lg font-bold text-white">Level {levelData.currentLevel}</h3>
                        <p className="text-sm text-[var(--color-text-secondary)]">
                          {levelData.totalXP.toLocaleString()} Total XP
                        </p>
                      </div>
                    </div>
                    <Badge variant="outline" className="text-primary border-primary/30">
                      {levelData.xpNeededForNextLevel} XP to next level
                    </Badge>
                  </div>

                  {/* XP Progress Bar */}
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span className="text-[var(--color-text-secondary)]">
                        {levelData.currentLevelXP} / {levelData.nextLevelXP} XP
                      </span>
                      <span className="text-primary font-medium">
                        {Math.round(levelData.levelProgressPercentage)}%
                      </span>
                    </div>
                    <div className="h-3 bg-[var(--color-container-bg)] rounded-full overflow-hidden">
                      <motion.div
                        className="h-full bg-gradient-to-r from-primary to-primary/80 rounded-full"
                        initial={{ width: 0 }}
                        animate={{ width: `${levelData.levelProgressPercentage}%` }}
                        transition={{ duration: 1, ease: "easeOut" }}
                      />
                    </div>
                  </div>
                </div>
              )}

              {/* Quick Stats */}
              <div className="grid grid-cols-2 gap-4">
                <div className="p-4 bg-[var(--color-container-bg)] rounded-lg">
                  <div className="flex items-center gap-2 mb-2">
                    <Trophy className="h-4 w-4 text-yellow-400" />
                    <span className="text-sm text-[var(--color-text-secondary)]">Achievements</span>
                  </div>
                  <div className="text-xl font-bold text-white">{achievements.length}</div>
                </div>
                
                {voiceStreakStats && (
                  <div className="p-4 bg-[var(--color-container-bg)] rounded-lg">
                    <div className="flex items-center gap-2 mb-2">
                      <Flame className={`h-4 w-4 ${voiceStreakStats.currentStreak > 0 ? 'text-orange-400' : 'text-gray-400'}`} />
                      <span className="text-sm text-[var(--color-text-secondary)]">Voice Streak</span>
                    </div>
                    <div className="text-xl font-bold text-white">
                      {voiceStreakStats.currentStreak} days
                    </div>
                  </div>
                )}
              </div>

              {/* Recent Achievements */}
              {achievements.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Award className="h-4 w-4" />
                    Recent Achievements
                  </h4>
                  <div className="space-y-2">
                    {achievements.slice(0, 3).map((achievement) => (
                      <div
                        key={achievement.id}
                        className={`p-3 rounded-lg border ${getRarityColor(achievement.achievement.rarity)} bg-[var(--color-container-bg)]/50`}
                      >
                        <div className="flex items-center justify-between">
                          <div>
                            <h5 className="font-medium text-white">{achievement.achievement.name}</h5>
                            <p className="text-xs text-[var(--color-text-secondary)]">
                              {achievement.unlockTimeDisplay}
                            </p>
                          </div>
                          <Badge variant="outline" className="text-primary border-primary/30">
                            +{achievement.xpAwarded} XP
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </motion.div>
          )}

          {activeTab === 'achievements' && (
            <motion.div
              key="achievements"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
              className="space-y-6"
            >
              {/* Completed Achievements */}
              {achievements.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Trophy className="h-4 w-4" />
                    Completed ({achievements.length})
                  </h4>
                  <div className="space-y-2 max-h-48 overflow-y-auto">
                    {achievements.map((achievement) => (
                      <div
                        key={achievement.id}
                        className={`p-3 rounded-lg border ${getRarityColor(achievement.achievement.rarity)} bg-[var(--color-container-bg)]/50`}
                      >
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <h5 className="font-medium text-white">{achievement.achievement.name}</h5>
                            <p className="text-sm text-[var(--color-text-secondary)] mt-1">
                              {achievement.achievement.description}
                            </p>
                            <p className="text-xs text-[var(--color-text-tertiary)] mt-1">
                              Unlocked {achievement.unlockTimeDisplay}
                            </p>
                          </div>
                          <div className="text-right ml-3">
                            <Badge variant="outline" className="text-primary border-primary/30 mb-1">
                              +{achievement.xpAwarded} XP
                            </Badge>
                            <div className="text-xs text-[var(--color-text-secondary)]">
                              {achievement.achievement.tier}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Available Achievements */}
              {availableAchievements.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-sm font-medium text-[var(--color-text-secondary)] flex items-center gap-2">
                    <Target className="h-4 w-4" />
                    Available ({availableAchievements.length})
                  </h4>
                  <div className="space-y-2 max-h-48 overflow-y-auto">
                    {availableAchievements.map((achievement) => (
                      <div
                        key={achievement.id}
                        className="p-3 rounded-lg border border-gray-600 bg-[var(--color-container-bg)]/30 opacity-75"
                      >
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <h5 className="font-medium text-white">{achievement.name}</h5>
                            <p className="text-sm text-[var(--color-text-secondary)] mt-1">
                              {achievement.description}
                            </p>
                          </div>
                          <div className="text-right ml-3">
                            <Badge variant="outline" className="text-gray-400 border-gray-600 mb-1">
                              {achievement.xpReward} XP
                            </Badge>
                            <div className="text-xs text-[var(--color-text-secondary)]">
                              {achievement.tier}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {achievements.length === 0 && availableAchievements.length === 0 && (
                <div className="text-center py-8 text-[var(--color-text-secondary)]">
                  <Trophy className="h-12 w-12 mx-auto mb-3 opacity-50" />
                  <p>No achievements data available</p>
                </div>
              )}
            </motion.div>
          )}

          {activeTab === 'streaks' && (
            <motion.div
              key="streaks"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ duration: 0.3 }}
              className="space-y-6"
            >
              {voiceStreakStats ? (
                <>
                  {/* Current Streak */}
                  <div className="p-4 bg-gradient-to-br from-orange-500/10 to-red-500/10 rounded-lg border border-orange-500/20">
                    <div className="flex items-center gap-3 mb-3">
                      <Flame className={`h-6 w-6 ${voiceStreakStats.currentStreak > 0 ? 'text-orange-400' : 'text-gray-400'}`} />
                      <h4 className="text-lg font-bold text-white">Current Streak</h4>
                    </div>
                    <div className="text-3xl font-bold text-orange-400 mb-2">
                      {voiceStreakStats.currentStreak} days
                    </div>
                    <p className="text-sm text-[var(--color-text-secondary)]">
                      {voiceStreakStats.hasActivityToday ? '🔥 Active today!' : 'No activity today'}
                    </p>
                  </div>

                  {/* Streak Stats */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="p-4 bg-[var(--color-container-bg)] rounded-lg">
                      <div className="flex items-center gap-2 mb-2">
                        <TrendingUp className="h-4 w-4 text-blue-400" />
                        <span className="text-sm text-[var(--color-text-secondary)]">Best Streak</span>
                      </div>
                      <div className="text-xl font-bold text-white">
                        {voiceStreakStats.highestStreak} days
                      </div>
                    </div>
                    
                    <div className="p-4 bg-[var(--color-container-bg)] rounded-lg">
                      <div className="flex items-center gap-2 mb-2">
                        <Calendar className="h-4 w-4 text-green-400" />
                        <span className="text-sm text-[var(--color-text-secondary)]">Active Days</span>
                      </div>
                      <div className="text-xl font-bold text-white">
                        {voiceStreakStats.totalActiveDays}
                      </div>
                    </div>
                  </div>

                  {/* Voice Time Stats */}
                  <div className="p-4 bg-[var(--color-container-bg)] rounded-lg">
                    <h5 className="font-medium text-white mb-3">Total Voice Time</h5>
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-2xl font-bold text-primary">
                          {voiceStreakStats.totalVoiceHours}h {voiceStreakStats.totalVoiceMinutes % 60}m
                        </div>
                        <div className="text-sm text-[var(--color-text-secondary)]">
                          {voiceStreakStats.totalVoiceMinutes.toLocaleString()} minutes total
                        </div>
                      </div>
                    </div>
                  </div>
                </>
              ) : (
                <div className="text-center py-8 text-[var(--color-text-secondary)]">
                  <Flame className="h-12 w-12 mx-auto mb-3 opacity-50" />
                  <p>No voice streak data available</p>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </CardContent>
    </Card>
  );
}; 