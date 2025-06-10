import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Trophy, Zap, Flame, Star, TrendingUp, Award, Target, Calendar, Info, MessageSquare, HelpCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/valorant/badge';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/valorant/tooltip';
import { useTheme } from '@/contexts/ThemeContext';
import httpClient from '@/lib/api/httpClient';

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
  const { theme } = useTheme();
  const [levelData, setLevelData] = useState<PairLevelData | null>(null);
  const [achievements, setAchievements] = useState<PairAchievement[]>([]);
  const [availableAchievements, setAvailableAchievements] = useState<Achievement[]>([]);
  const [voiceStreakStats, setVoiceStreakStats] = useState<VoiceStreakStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'overview' | 'achievements' | 'streaks'>('overview');
  const [openTooltip, setOpenTooltip] = useState<string | null>(null);

  // Handle tooltip click
  const handleTooltipClick = (tooltipId: string) => {
    setOpenTooltip(openTooltip === tooltipId ? null : tooltipId);
  };

  // Close tooltip when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (openTooltip && !(event.target as Element).closest('[data-tooltip-trigger]')) {
        setOpenTooltip(null);
      }
    };

    if (openTooltip) {
      document.addEventListener('click', handleClickOutside);
      return () => document.removeEventListener('click', handleClickOutside);
    }
  }, [openTooltip]);

  // Fetch XP data
  const fetchXPData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [levelResponse, achievementsResponse, availableResponse, streaksResponse] = await Promise.allSettled([
        httpClient.get(`/pairings/${pairingId}/level`),
        httpClient.get(`/pairings/${pairingId}/achievements`),
        httpClient.get(`/pairings/${pairingId}/achievements/available`),
        httpClient.get(`/pairings/${pairingId}/streaks`)
      ]);

      // Handle level data
      if (levelResponse.status === 'fulfilled' && levelResponse.value.status === 200) {
        setLevelData(levelResponse.value.data);
      }

      // Handle achievements
      if (achievementsResponse.status === 'fulfilled' && achievementsResponse.value.status === 200) {
        setAchievements(achievementsResponse.value.data);
      }

      // Handle available achievements
      if (availableResponse.status === 'fulfilled' && availableResponse.value.status === 200) {
        setAvailableAchievements(availableResponse.value.data);
      } else {
        // If API fails, show example achievements for guidance
        setAvailableAchievements([
          {
            id: 1,
            achievementKey: "FIRST_STEPS",
            name: "First Steps",
            description: "Send your first 10 messages",
            achievementType: "MESSAGE_MILESTONE",
            xpReward: 50,
            rarity: "COMMON",
            tier: "BRONZE"
          },
          {
            id: 2,
            achievementKey: "VOICE_STREAK_3",
            name: "Voice Enthusiast",
            description: "Maintain a 3-day voice streak",
            achievementType: "VOICE_STREAK",
            xpReward: 100,
            rarity: "COMMON",
            tier: "BRONZE"
          },
          {
            id: 3,
            achievementKey: "WEEK_WARRIOR",
            name: "Week Warrior",
            description: "Stay active for 1 full week",
            achievementType: "WEEKLY_ACTIVITY",
            xpReward: 100,
            rarity: "COMMON",
            tier: "BRONZE"
          }
        ]);
      }

      // Handle voice streaks
      if (streaksResponse.status === 'fulfilled' && streaksResponse.value.status === 200) {
        setVoiceStreakStats(streaksResponse.value.data.statistics);
      }

    } catch (err) {
      console.error('Error fetching XP data:', err);
      setError('Failed to load XP data');
      
      // Show example achievements even when there's an error
      setAvailableAchievements([
        {
          id: 1,
          achievementKey: "FIRST_STEPS",
          name: "First Steps",
          description: "Send your first 10 messages",
          achievementType: "MESSAGE_MILESTONE",
          xpReward: 50,
          rarity: "COMMON",
          tier: "BRONZE"
        },
        {
          id: 2,
          achievementKey: "VOICE_STREAK_3", 
          name: "Voice Enthusiast",
          description: "Maintain a 3-day voice streak",
          achievementType: "VOICE_STREAK",
          xpReward: 100,
          rarity: "COMMON",
          tier: "BRONZE"
        }
      ]);
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
              <div className="h-20 bg-theme-container rounded-lg animate-pulse" />
              <div className="h-16 bg-theme-container rounded-lg animate-pulse" />
              <div className="h-12 bg-theme-container rounded-lg animate-pulse" />
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
    <TooltipProvider>
      <motion.div layout transition={{ duration: 0.3, ease: "easeInOut" }}>
        <Card className={`valorant-card bg-theme-card border-theme theme-transition ${className}`}>
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
                  className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium theme-transition ${
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

          <CardContent>
            <motion.div layout transition={{ duration: 0.3, ease: "easeInOut" }}>
              <AnimatePresence mode="wait">
                {activeTab === 'overview' && (
                  <motion.div
                    key="overview"
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -20 }}
                    transition={{ duration: 0.3 }}
                    className="space-y-6"
                    layout
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
                          <p className="text-sm text-theme-secondary">
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
                        <span className="text-theme-secondary">
                          {levelData.currentLevelXP} / {levelData.nextLevelXP} XP
                        </span>
                        <span className="text-primary font-medium">
                          {Math.round(levelData.levelProgressPercentage)}%
                        </span>
                      </div>
                      <div className="xp-progress-container">
                        <div className="xp-progress-bar">
                          <motion.div
                            className="xp-progress-fill"
                            initial={{ width: 0 }}
                            animate={{ width: `${levelData.levelProgressPercentage}%` }}
                            transition={{ duration: 1, ease: "easeOut" }}
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Quick Stats */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                    <div className="flex items-center gap-2 mb-2">
                      <Trophy className="h-4 w-4 text-yellow-400" />
                      <span className="text-sm text-theme-secondary">Achievements</span>
                    </div>
                    <div className="text-xl font-bold text-white">{achievements.length}</div>
                  </div>
                  
                  {voiceStreakStats && (
                    <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                      <div className="flex items-center gap-2 mb-2">
                        <Flame className={`h-4 w-4 ${voiceStreakStats.currentStreak > 0 ? 'text-orange-400' : 'text-gray-400'}`} />
                        <span className="text-sm text-theme-secondary">Voice Streak</span>
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
                    <h4 className="text-sm font-medium text-theme-secondary flex items-center gap-2">
                      <Award className="h-4 w-4" />
                      Recent Achievements
                    </h4>
                    <div className="space-y-2">
                      {achievements.slice(0, 3).map((achievement) => (
                        <div
                          key={achievement.id}
                          className={`p-3 rounded-lg border ${getRarityColor(achievement.achievement.rarity)} bg-theme-container theme-transition`}
                        >
                          <div className="flex items-center justify-between">
                            <div>
                              <h5 className="font-medium text-white">{achievement.achievement.name}</h5>
                              <p className="text-xs text-theme-secondary">
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
                layout
              >
                {/* Completed Achievements */}
                {achievements.length > 0 && (
                  <div className="space-y-3">
                    <h4 className="text-sm font-medium text-theme-secondary flex items-center gap-2">
                      <Trophy className="h-4 w-4" />
                      Completed ({achievements.length})
                    </h4>
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {achievements.map((achievement) => (
                        <div
                          key={achievement.id}
                          className={`p-3 rounded-lg border ${getRarityColor(achievement.achievement.rarity)} bg-theme-container theme-transition`}
                        >
                          <div className="flex items-start justify-between">
                            <div className="flex-1">
                              <h5 className="font-medium text-white">{achievement.achievement.name}</h5>
                              <p className="text-sm text-theme-secondary mt-1">
                                {achievement.achievement.description}
                              </p>
                              <p className="text-xs text-theme-tertiary mt-1">
                                Unlocked {achievement.unlockTimeDisplay}
                              </p>
                            </div>
                            <div className="text-right ml-3">
                              <Badge variant="outline" className="text-primary border-primary/30 mb-1">
                                +{achievement.xpAwarded} XP
                              </Badge>
                              <div className="text-xs text-theme-secondary">
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
                    <h4 className="text-sm font-medium text-theme-secondary flex items-center gap-2">
                      <Target className="h-4 w-4" />
                      Available ({availableAchievements.length})
                    </h4>
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {availableAchievements.map((achievement) => (
                        <div
                          key={achievement.id}
                          className="p-3 rounded-lg border border-gray-600 opacity-75 bg-theme-card theme-transition"
                        >
                          <div className="flex items-start justify-between">
                            <div className="flex-1">
                              <h5 className="font-medium text-white">{achievement.name}</h5>
                              <p className="text-sm text-theme-secondary mt-1">
                                {achievement.description}
                              </p>
                            </div>
                            <div className="text-right ml-3">
                              <Badge variant="outline" className="text-gray-400 border-gray-600 mb-1">
                                {achievement.xpReward} XP
                              </Badge>
                              <div className="text-xs text-theme-secondary">
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
                  <div className="space-y-6">
                    <div className="text-center py-6">
                      <Trophy className="h-16 w-16 mx-auto mb-4 text-theme-tertiary" />
                      <h3 className="text-xl font-bold text-white mb-2">Start Your Achievement Journey!</h3>
                      <p className="text-theme-secondary mb-6">
                        No achievements unlocked yet. Here's how you can earn them:
                      </p>
                    </div>

                    {/* Achievement Categories Guide */}
                    <div className="grid gap-3">
                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-3 mb-2">
                          <MessageSquare className="h-5 w-5 text-blue-400" />
                          <h4 className="font-semibold text-white">Message Milestones</h4>
                        </div>
                        <p className="text-sm text-theme-secondary">
                          Send 10, 50, 100, 500, or 1000+ messages to unlock achievements. Every 1000 messages = 100 XP!
                        </p>
                      </div>

                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-3 mb-2">
                          <Flame className="h-5 w-5 text-orange-400" />
                          <h4 className="font-semibold text-white">Voice Streaks</h4>
                        </div>
                        <p className="text-sm text-theme-secondary">
                          Talk for 30+ minutes daily to build streaks. Achieve 3, 7, 14, or 30+ day streaks for rewards!
                        </p>
                      </div>

                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-3 mb-2">
                          <Calendar className="h-5 w-5 text-green-400" />
                          <h4 className="font-semibold text-white">Weekly Activity</h4>
                        </div>
                        <p className="text-sm text-theme-secondary">
                          Stay active for 1, 4, 12, or 26+ weeks. Consistent activity earns 100 XP per week!
                        </p>
                      </div>

                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-3 mb-2">
                          <Info className="h-5 w-5 text-purple-400" />
                          <h4 className="font-semibold text-white">Special Achievements</h4>
                        </div>
                        <p className="text-sm text-theme-secondary">
                          Voice time milestones, compatibility bonuses, and longevity rewards await!
                        </p>
                      </div>
                    </div>

                    <div className="text-center p-4 bg-primary/10 rounded-lg border border-primary/20">
                      <p className="text-sm text-primary font-medium">
                        💡 Tip: Start chatting and using voice to begin earning your first achievements!
                      </p>
                    </div>
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
                layout
              >
                {voiceStreakStats ? (
                  <>
                    {/* Current Streak */}
                    <div className="p-4 bg-theme-container border-theme theme-transition rounded-lg">
                      <div className="flex items-center gap-3 mb-3">
                        <Flame className={`h-6 w-6 ${voiceStreakStats.currentStreak > 0 ? 'text-orange-400' : 'text-gray-400'}`} />
                        <h4 className="text-lg font-bold text-white">Current Streak</h4>
                        <Tooltip open={openTooltip === 'current-streak'} onOpenChange={() => {}}>
                          <TooltipTrigger asChild>
                            <button 
                              className="p-1 hover:bg-orange-500/20 rounded"
                              onClick={() => handleTooltipClick('current-streak')}
                              data-tooltip-trigger
                            >
                              <HelpCircle className="h-4 w-4 text-orange-400/70" />
                            </button>
                          </TooltipTrigger>
                          <TooltipContent className="max-w-sm bg-theme-container border border-orange-500/30 text-white">
                            <div className="space-y-3">
                              <div>
                                <p className="font-medium text-orange-400">Current Streak:</p>
                                <p className="text-xs">
                                  {voiceStreakStats.currentStreak > 0 
                                    ? `You've been active for ${voiceStreakStats.currentStreak} consecutive days! Keep it up!`
                                    : "Start your streak by using voice chat for 30+ minutes today!"
                                  }
                                </p>
                              </div>
                              <div>
                                <p className="font-medium text-orange-400">How Voice Streaks Work:</p>
                                <ul className="text-xs space-y-1">
                                  <li>• Spend 30+ minutes in voice chat daily</li>
                                  <li>• Streak increases by 1 each active day</li>
                                  <li>• Missing a day resets your streak to 0</li>
                                  <li>• Streaks carry over between sessions</li>
                                </ul>
                              </div>
                            </div>
                          </TooltipContent>
                        </Tooltip>
                      </div>
                      <div className="text-3xl font-bold text-orange-400 mb-2">
                        {voiceStreakStats.currentStreak} days
                      </div>
                      <p className="text-sm text-theme-secondary">
                        {voiceStreakStats.hasActivityToday ? '🔥 Active today!' : 'No activity today'}
                      </p>
                    </div>

                    {/* Streak Stats */}
                    <div className="grid grid-cols-2 gap-4">
                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-2 mb-2">
                          <TrendingUp className="h-4 w-4 text-blue-400" />
                          <span className="text-sm text-theme-secondary">Best Streak</span>
                          <Tooltip open={openTooltip === 'best-streak'} onOpenChange={() => {}}>
                            <TooltipTrigger asChild>
                              <button 
                                className="p-1 hover:bg-blue-500/20 rounded"
                                onClick={() => handleTooltipClick('best-streak')}
                                data-tooltip-trigger
                              >
                                <HelpCircle className="h-3 w-3 text-blue-400/70" />
                              </button>
                            </TooltipTrigger>
                            <TooltipContent className="bg-theme-container border border-blue-500/30 text-white">
                              <p className="text-xs">Your longest consecutive daily voice streak</p>
                            </TooltipContent>
                          </Tooltip>
                        </div>
                        <div className="text-xl font-bold text-white">
                          {voiceStreakStats.highestStreak} days
                        </div>
                      </div>
                      
                      <div className="p-4 rounded-lg bg-theme-container border-theme theme-transition">
                        <div className="flex items-center gap-2 mb-2">
                          <Calendar className="h-4 w-4 text-green-400" />
                          <span className="text-sm text-theme-secondary">Active Days</span>
                          <Tooltip open={openTooltip === 'active-days'} onOpenChange={() => {}}>
                            <TooltipTrigger asChild>
                              <button 
                                className="p-1 hover:bg-green-500/20 rounded"
                                onClick={() => handleTooltipClick('active-days')}
                                data-tooltip-trigger
                              >
                                <HelpCircle className="h-3 w-3 text-green-400/70" />
                              </button>
                            </TooltipTrigger>
                            <TooltipContent className="bg-theme-container border border-green-500/30 text-white">
                              <p className="text-xs">Total days you've used voice chat for 30+ minutes</p>
                            </TooltipContent>
                          </Tooltip>
                        </div>
                        <div className="text-xl font-bold text-white">
                          {voiceStreakStats.totalActiveDays}
                        </div>
                      </div>
                    </div>



                    {/* Streak Milestones */}
                    <div className="space-y-3">
                      <div className="flex items-center gap-2">
                        <Trophy className="h-4 w-4 text-yellow-400" />
                        <h4 className="text-sm font-medium text-theme-secondary">Streak Milestones & XP Rewards</h4>
                        <Tooltip open={openTooltip === 'streak-rewards'} onOpenChange={() => {}}>
                          <TooltipTrigger asChild>
                            <button 
                              className="p-1 hover:bg-yellow-500/20 rounded"
                              onClick={() => handleTooltipClick('streak-rewards')}
                              data-tooltip-trigger
                            >
                              <HelpCircle className="h-4 w-4 text-yellow-400/70" />
                            </button>
                          </TooltipTrigger>
                          <TooltipContent className="max-w-sm bg-theme-container border border-yellow-500/30 text-white">
                            <div className="space-y-2">
                              <p className="font-medium text-yellow-400">Streak XP Rewards:</p>
                              <ul className="text-xs space-y-1">
                                <li>• 3 days: 50 XP bonus</li>
                                <li>• 7 days: 100 XP bonus</li>
                                <li>• 14 days: 200 XP bonus</li>
                                <li>• 30 days: 500 XP bonus</li>
                                <li>• Plus regular voice XP: 25 XP per hour</li>
                              </ul>
                            </div>
                          </TooltipContent>
                        </Tooltip>
                      </div>
                      
                      <div className="grid grid-cols-2 gap-3">
                        {[
                          { days: 3, xp: 50, color: 'green', achieved: voiceStreakStats.highestStreak >= 3 },
                          { days: 7, xp: 100, color: 'blue', achieved: voiceStreakStats.highestStreak >= 7 },
                          { days: 14, xp: 200, color: 'purple', achieved: voiceStreakStats.highestStreak >= 14 },
                          { days: 30, xp: 500, color: 'yellow', achieved: voiceStreakStats.highestStreak >= 30 }
                        ].map(({ days, xp, color, achieved }) => (
                          <div
                            key={days}  
                            className={`p-3 rounded-lg border ${
                              achieved 
                                ? `border-${color}-500/50 bg-${color}-500/10` 
                                : 'border-gray-600 bg-gray-500/10'
                            }`}
                          >
                            <div className="flex items-center justify-between">
                              <div>
                                <div className={`text-sm font-medium ${achieved ? `text-${color}-400` : 'text-gray-400'}`}>
                                  {days} Days
                                </div>
                                <div className="text-xs text-theme-secondary">
                                  +{xp} XP
                                </div>
                              </div>
                              {achieved ? (
                                <Trophy className={`h-4 w-4 text-${color}-400`} />
                              ) : (
                                <Target className="h-4 w-4 text-gray-400" />
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  </>
                ) : (
                                      <div className="text-center py-8 text-theme-secondary">
                    <Flame className="h-12 w-12 mx-auto mb-3 opacity-50" />
                    <p>No voice streak data available</p>
                    <p className="text-xs mt-2">Start using voice chat to begin tracking your streaks!</p>
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
            </motion.div>
          </CardContent>
        </Card>
      </motion.div>
    </TooltipProvider>
  );
}; 