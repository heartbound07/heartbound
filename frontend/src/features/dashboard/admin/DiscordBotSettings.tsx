import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { 
  HiOutlineCheck, 
  HiOutlineInformationCircle, 
  HiOutlineLightningBolt, 
  HiOutlineChat, 
  HiOutlineClock, 
  HiOutlineChartBar, 
  HiOutlineBadgeCheck, 
  HiOutlineCalculator,
} from 'react-icons/hi';
import { FiActivity, FiSettings, FiUsers, FiAward } from 'react-icons/fi';
import { motion } from 'framer-motion';

interface DiscordBotSettingsData {
  activityEnabled: boolean;
  creditsToAward: number;
  messageThreshold: number;
  timeWindowMinutes: number;
  cooldownSeconds: number;
  minMessageLength: number;
  levelingEnabled: boolean;
  xpToAward: number;
  baseXp: number;
  levelMultiplier: number;
  levelExponent: number;
  levelFactor: number;
  creditsPerLevel: number;
  level5RoleId: string;
  level15RoleId: string;
  level30RoleId: string;
  level40RoleId: string;
  level50RoleId: string;
  level70RoleId: string;
  level100RoleId: string;
  inactivityChannelId: string;
  countingGameEnabled: boolean;
  countingChannelId: string;
  countingTimeoutRoleId: string;
  creditsPerCount: number;
  countingLives: number;
}

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

const initialSettings: DiscordBotSettingsData = {
  activityEnabled: true,
  creditsToAward: 5,
  messageThreshold: 5,
  timeWindowMinutes: 60,
  cooldownSeconds: 30,
  minMessageLength: 15,
  levelingEnabled: true,
  xpToAward: 15,
  baseXp: 100,
  levelMultiplier: 50,
  levelExponent: 2,
  levelFactor: 5,
  creditsPerLevel: 50,
  level5RoleId: "1161732022704816250",
  level15RoleId: "1162632126068437063",
  level30RoleId: "1162628059296432148",
  level40RoleId: "1162628114195697794",
  level50RoleId: "1166539666674167888",
  level70RoleId: "1170429914185465906",
  level100RoleId: "1162628179043823657",
  inactivityChannelId: "",
  countingGameEnabled: false,
  countingChannelId: "",
  countingTimeoutRoleId: "",
  creditsPerCount: 1,
  countingLives: 3
};

const calculateRequiredXp = (level: number, baseXp: number, levelMultiplier: number, levelExponent: number, levelFactor: number): number => {
  return Math.floor(baseXp + (levelMultiplier * Math.pow(level, levelExponent) / levelFactor));
};

export function DiscordBotSettings() {
  const { hasRole } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [settings, setSettings] = useState<DiscordBotSettingsData>(initialSettings);
  const [error, setError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [targetLevel, setTargetLevel] = useState<number>(10);
  const [estimationResults, setEstimationResults] = useState<{
    messagesNeeded: number;
    timeNeeded: string;
    totalXpRequired: number;
    creditsEarned: number;
    realisticTimeNeeded: string;
  } | null>(null);
  
  // Additional security check - redirect if not admin
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }

  // Toast notification functions
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    const toastId = Math.random().toString(36).substring(2, 9);
    
    // Don't add duplicate toasts with the same message and type
    if (!toasts.some(toast => toast.message === message && toast.type === type)) {
      setToasts(prev => [...prev, { id: toastId, message, type }]);
    }
  };
  
  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  };
  
  useEffect(() => {
    const fetchSettings = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        const response = await httpClient.get('/admin/discord/settings');
        
        setSettings(response.data);
      } catch (err) {
        console.error('Failed to fetch Discord bot settings:', err);
        setError('Failed to load settings. Please try again.');
        showToast('Failed to load Discord bot settings', 'error');
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchSettings();
  }, []);
  
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type } = e.target;
    
    // Add validation for role ID and channel ID fields
    if (name.endsWith('RoleId') || name.endsWith('ChannelId')) {
      // Allow empty string or digits only
      if (value === '' || /^\d+$/.test(value)) {
        setSettings({
          ...settings,
          [name]: value
        });
      }
      // Don't update state if invalid input
      return;
    }
    
    if (type === 'checkbox') {
      setSettings(prevSettings => ({
        ...prevSettings,
        [name]: e.target.checked
      }));
    } else if (type === 'number') {
      // Handle empty string case to avoid NaN
      const numValue = value === '' ? 0 : parseInt(value, 10);
      setSettings(prevSettings => ({
        ...prevSettings,
        [name]: isNaN(numValue) ? 0 : numValue
      }));
    } else {
      setSettings(prevSettings => ({
        ...prevSettings,
        [name]: value
      }));
    }
  };
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setIsSaving(true);
      setError(null);
      
      await httpClient.put('/admin/discord/settings', settings);
      
      showToast('Discord bot settings updated successfully', 'success');
    } catch (err) {
      console.error('Failed to update Discord bot settings:', err);
      setError('Failed to save settings. Please check your inputs and try again.');
      showToast('Failed to update Discord bot settings', 'error');
    } finally {
      setIsSaving(false);
    }
  };
  
  const calculateLevelProgression = () => {
    // Validate target level
    if (!targetLevel || targetLevel < 2) {
      showToast('Target level must be at least 2', 'error');
      return;
    }
    
    let totalXpRequired = 0;
    // Sum up XP required for all levels from 1 to target
    for (let level = 1; level < targetLevel; level++) {
      totalXpRequired += calculateRequiredXp(
        level, 
        settings.baseXp, 
        settings.levelMultiplier, 
        settings.levelExponent, 
        settings.levelFactor
      );
    }
    
    // Calculate messages needed based on XP per message
    const messagesNeeded = Math.ceil(totalXpRequired / (settings.xpToAward || 1));
    
    // Perfect-case time calculation (assuming messages sent at exact cooldown)
    const timeInSeconds = messagesNeeded * (settings.cooldownSeconds || 1);
    const days = Math.floor(timeInSeconds / 86400);
    const hours = Math.floor((timeInSeconds % 86400) / 3600);
    const minutes = Math.floor((timeInSeconds % 3600) / 60);
    
    // Realistic scenario - assumes average of 2 hours of active chatting per day
    // and messages sent at 2x the minimum cooldown rate on average
    const activeHoursPerDay = 2;
    const messageRateMultiplier = 2;
    const realisticTimeInSeconds = timeInSeconds * messageRateMultiplier;
    const realisticTotalDays = Math.ceil(realisticTimeInSeconds / (activeHoursPerDay * 3600));
    
    const realisticWeeks = Math.floor(realisticTotalDays / 7);
    const realisticDays = realisticTotalDays % 7;
    
    // Calculate credits earned from leveling
    const creditsEarned = (targetLevel - 1) * (settings.creditsPerLevel || 0);
    
    const timeNeeded = days > 0 
      ? `${days} days, ${hours} hours, ${minutes} minutes` 
      : hours > 0 
        ? `${hours} hours, ${minutes} minutes` 
        : `${minutes} minutes`;
        
    const realisticTimeNeeded = realisticWeeks > 0
      ? `${realisticWeeks} weeks and ${realisticDays} days`
      : `${realisticTotalDays} days`;
        
    setEstimationResults({
      messagesNeeded,
      timeNeeded,
      totalXpRequired,
      creditsEarned,
      realisticTimeNeeded
    });
  };
  
  // Loading state view with animation
  if (isLoading) {
    return (
      <div className="container mx-auto p-6">
        <div className="flex flex-col items-center justify-center h-96">
          <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin mb-4"></div>
          <p className="text-white text-lg">Loading Discord Bot Settings...</p>
        </div>
      </div>
    );
  }
  
  return (
    <div className="container mx-auto p-4 md:p-6 max-w-6xl">
      {/* Toast notifications */}
      <div className="fixed top-4 right-4 z-50 space-y-2">
        {toasts.map(toast => (
          <motion.div
            key={toast.id}
            initial={{ opacity: 0, x: 50 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 50 }}
          >
            <Toast
              key={toast.id}
              message={toast.message}
              type={toast.type}
              onClose={() => removeToast(toast.id)}
            />
          </motion.div>
        ))}
      </div>
      
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white mb-2 flex items-center">
          <FiSettings className="mr-3 text-primary" size={28} />
          Discord Bot Settings
        </h1>
        <p className="text-slate-400 max-w-3xl">
          Configure how the Discord bot awards activity credits, manages the leveling system, and assigns role rewards.
        </p>
      </div>
      
      {/* Error message */}
      {error && (
        <motion.div 
          className="mb-6 bg-red-900/40 border border-red-500/50 rounded-lg p-4 text-red-200"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <p className="flex items-center">
            <HiOutlineInformationCircle className="mr-2 flex-shrink-0" size={20} />
            {error}
          </p>
        </motion.div>
      )}
      
      <form onSubmit={handleSubmit}>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left Column - Activity Settings */}
          <div>
            {/* Activity Credits Settings Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
              <div className="flex items-center mb-4">
                <HiOutlineChat className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Activity Credits Settings
                </h2>
              </div>
              
              <div className="space-y-6">
                <div className="relative flex items-center mt-2">
                  <div className="flex items-center h-5">
                    <input
                      type="checkbox"
                      id="activityEnabled"
                      name="activityEnabled"
                      checked={settings.activityEnabled}
                      onChange={handleChange}
                      className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
                    />
                  </div>
                  <div className="ml-3 text-white">
                    <label htmlFor="activityEnabled" className="text-base font-medium flex items-center">
                      Enable Activity Credits
                      <div className="group relative ml-2">
                        <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={18} />
                        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                          When enabled, users earn credits for being active in Discord chat.
                        </div>
                      </div>
                    </label>
                    <p className="text-slate-400 text-sm mt-1">Award credits for active participation in Discord</p>
                  </div>
                </div>
                
                <div className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex justify-between">
                      <label htmlFor="creditsToAward" className="text-white flex items-center">
                        Credits to Award
                        <div className="group relative ml-2">
                          <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={16} />
                          <div className="absolute bottom-full left-0 mb-2 w-60 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                            Credits awarded when a user meets the message threshold
                          </div>
                        </div>
                      </label>
                      <span className="text-primary font-semibold">{settings.creditsToAward}</span>
                    </div>
                    <input
                      type="range"
                      id="creditsToAward"
                      name="creditsToAward"
                      value={settings.creditsToAward}
                      onChange={handleChange}
                      min="1"
                      max="50"
                      className="w-full appearance-none h-2 bg-slate-700 rounded-lg outline-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:w-4 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-primary"
                    />
                    <input
                      type="number"
                      id="creditsToAwardExact"
                      name="creditsToAward"
                      value={settings.creditsToAward || ''}
                      onChange={handleChange}
                      min="1"
                      className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label htmlFor="messageThreshold" className="block text-white">
                      Message Threshold
                    </label>
                    <input
                      type="number"
                      id="messageThreshold"
                      name="messageThreshold"
                      value={settings.messageThreshold || ''}
                      onChange={handleChange}
                      min="1"
                      className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                    />
                    <p className="text-slate-400 text-sm">Number of messages required within the time window to earn credits</p>
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <label htmlFor="timeWindowMinutes" className="block text-white flex items-center">
                        <HiOutlineClock className="mr-1.5" size={16} />
                        Time Window (minutes)
                      </label>
                      <input
                        type="number"
                        id="timeWindowMinutes"
                        name="timeWindowMinutes"
                        value={settings.timeWindowMinutes || ''}
                        onChange={handleChange}
                        min="1"
                        className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                      />
                      <p className="text-slate-400 text-sm">Period in which messages are counted</p>
                    </div>
                    
                    <div className="space-y-2">
                      <label htmlFor="cooldownSeconds" className="block text-white flex items-center">
                        <FiActivity className="mr-1.5" size={16} />
                        Cooldown (seconds)
                      </label>
                      <input
                        type="number"
                        id="cooldownSeconds"
                        name="cooldownSeconds"
                        value={settings.cooldownSeconds || ''}
                        onChange={handleChange}
                        min="1"
                        className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                      />
                      <p className="text-slate-400 text-sm">Cooldown between counting messages</p>
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <label htmlFor="minMessageLength" className="block text-white">
                      Minimum Message Length
                    </label>
                    <input
                      type="number"
                      id="minMessageLength"
                      name="minMessageLength"
                      value={settings.minMessageLength || ''}
                      onChange={handleChange}
                      min="1"
                      className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                    />
                    <p className="text-slate-400 text-sm">Minimum character length for a message to count</p>
                  </div>
                </div>
              </div>
            </div>
            
            {/* Leveling System Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
              <div className="flex items-center mb-4">
                <HiOutlineChartBar className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Leveling System Settings
                </h2>
              </div>
              
              <div className="space-y-6">
                <div className="relative flex items-center mt-2">
                  <div className="flex items-center h-5">
                    <input
                      type="checkbox"
                      id="levelingEnabled"
                      name="levelingEnabled"
                      checked={settings.levelingEnabled}
                      onChange={handleChange}
                      className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
                    />
                  </div>
                  <div className="ml-3 text-white">
                    <label htmlFor="levelingEnabled" className="text-base font-medium flex items-center">
                      Enable Leveling System
                      <div className="group relative ml-2">
                        <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={18} />
                        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                          When enabled, users earn XP and level up for being active in Discord
                        </div>
                      </div>
                    </label>
                    <p className="text-slate-400 text-sm mt-1">Award XP and levels for Discord activity</p>
                  </div>
                </div>
                
                <div className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <label htmlFor="xpToAward" className="block text-white flex items-center">
                        <HiOutlineLightningBolt className="mr-1.5" size={16} />
                        XP per Message
                      </label>
                      <input
                        type="number"
                        id="xpToAward"
                        name="xpToAward"
                        value={settings.xpToAward || ''}
                        onChange={handleChange}
                        min="1"
                        className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                      />
                      <p className="text-slate-400 text-sm">XP awarded for each valid message</p>
                    </div>
                    
                    <div className="space-y-2">
                      <label htmlFor="creditsPerLevel" className="block text-white flex items-center">
                        <FiAward className="mr-1.5" size={16} />
                        Credits per Level Up
                      </label>
                      <input
                        type="number"
                        id="creditsPerLevel"
                        name="creditsPerLevel"
                        value={settings.creditsPerLevel || ''}
                        onChange={handleChange}
                        min="1"
                        className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                      />
                      <p className="text-slate-400 text-sm">Credits awarded when a user levels up</p>
                    </div>
                  </div>
                  
                  {/* Level Formula Settings */}
                  <div className="p-4 bg-slate-800/50 rounded-lg border border-slate-700/50 mb-2">
                    <h3 className="text-white font-medium mb-3 flex items-center">
                      <HiOutlineCalculator className="mr-1.5" size={16} />
                      Level Formula Parameters
                    </h3>
                    
                    <p className="text-slate-300 text-sm mb-4">
                      Formula: <span className="font-mono bg-slate-800 px-2 py-0.5 rounded">baseXP + (levelMultiplier × level^exponent ÷ factor)</span>
                    </p>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <label htmlFor="baseXp" className="block text-white">
                          Base XP
                        </label>
                        <input
                          type="number"
                          id="baseXp"
                          name="baseXp"
                          value={settings.baseXp || ''}
                          onChange={handleChange}
                          min="1"
                          className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                        />
                        <p className="text-slate-400 text-sm">Base XP for level 1</p>
                      </div>
                      
                      <div className="space-y-2">
                        <label htmlFor="levelMultiplier" className="block text-white">
                          Level Multiplier
                        </label>
                        <input
                          type="number"
                          id="levelMultiplier"
                          name="levelMultiplier"
                          value={settings.levelMultiplier || ''}
                          onChange={handleChange}
                          min="1"
                          className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                        />
                        <p className="text-slate-400 text-sm">Multiplier for XP scaling</p>
                      </div>
                      
                      <div className="space-y-2">
                        <label htmlFor="levelExponent" className="block text-white">
                          Level Exponent
                        </label>
                        <input
                          type="number"
                          id="levelExponent"
                          name="levelExponent"
                          value={settings.levelExponent || ''}
                          onChange={handleChange}
                          min="1"
                          step="0.1"
                          className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                        />
                        <p className="text-slate-400 text-sm">Power to raise level to</p>
                      </div>
                      
                      <div className="space-y-2">
                        <label htmlFor="levelFactor" className="block text-white">
                          Level Factor
                        </label>
                        <input
                          type="number"
                          id="levelFactor"
                          name="levelFactor"
                          value={settings.levelFactor || ''}
                          onChange={handleChange}
                          min="1"
                          className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                        />
                        <p className="text-slate-400 text-sm">Divisor for XP scaling</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          {/* Right Column - Role Rewards and Calculator */}
          <div>
            {/* Level Role Rewards Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
              <div className="flex items-center mb-4">
                <FiUsers className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Level Role Rewards
                </h2>
              </div>
              
              <p className="text-slate-300 mb-4">Configure which Discord role IDs will be granted at each level milestone.</p>
              
              <div className="space-y-4">
                {/* Level role mapping with badges */}
                <div className="mt-5 space-y-4">
                  <h3 className="text-lg font-semibold text-white flex items-center">
                    <FiAward className="mr-2 text-primary" size={20} />
                    Discord Role IDs
                  </h3>
                  <p className="text-sm text-slate-400">
                    Configure the Discord role IDs that will be automatically assigned to users when they reach specific level milestones.
                  </p>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="level5RoleId" className="block text-sm font-medium text-slate-300">
                        Level 5 Role ID
                      </label>
                      <input
                        type="text"
                        id="level5RoleId"
                        name="level5RoleId"
                        value={settings.level5RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level15RoleId" className="block text-sm font-medium text-slate-300">
                        Level 15 Role ID
                      </label>
                      <input
                        type="text"
                        id="level15RoleId"
                        name="level15RoleId"
                        value={settings.level15RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level30RoleId" className="block text-sm font-medium text-slate-300">
                        Level 30 Role ID
                      </label>
                      <input
                        type="text"
                        id="level30RoleId"
                        name="level30RoleId"
                        value={settings.level30RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level40RoleId" className="block text-sm font-medium text-slate-300">
                        Level 40 Role ID
                      </label>
                      <input
                        type="text"
                        id="level40RoleId"
                        name="level40RoleId"
                        value={settings.level40RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level50RoleId" className="block text-sm font-medium text-slate-300">
                        Level 50 Role ID
                      </label>
                      <input
                        type="text"
                        id="level50RoleId"
                        name="level50RoleId"
                        value={settings.level50RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level70RoleId" className="block text-sm font-medium text-slate-300">
                        Level 70 Role ID
                      </label>
                      <input
                        type="text"
                        id="level70RoleId"
                        name="level70RoleId"
                        value={settings.level70RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                    
                    <div>
                      <label htmlFor="level100RoleId" className="block text-sm font-medium text-slate-300">
                        Level 100 Role ID
                      </label>
                      <input
                        type="text"
                        id="level100RoleId"
                        name="level100RoleId"
                        value={settings.level100RoleId || ''}
                        onChange={handleChange}
                        className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                        placeholder="Enter Discord Role ID"
                      />
                    </div>
                  </div>
                  
                  <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3 text-sm text-yellow-200">
                    <p className="font-medium flex items-center">
                      <HiOutlineInformationCircle className="mr-1.5" size={16} />
                      How to find Discord Role IDs
                    </p>
                    <p className="mt-1 text-yellow-100/80">
                      In Discord, enable Developer Mode in Settings → Advanced, then right-click on a role and select "Copy ID".
                    </p>
                  </div>
                </div>
              </div>
            </div>
            
            {/* Voice Activity Settings Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
              <div className="flex items-center mb-4">
                <FiActivity className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Voice Activity Settings
                </h2>
              </div>
              
              <p className="text-slate-300 mb-4">Configure voice channels where activity tracking should be disabled.</p>
              
              <div className="space-y-4">
                <div>
                  <label htmlFor="inactivityChannelId" className="block text-sm font-medium text-slate-300 mb-2">
                    Inactivity Channel ID
                  </label>
                  <input
                    type="text"
                    id="inactivityChannelId"
                    name="inactivityChannelId"
                    value={settings.inactivityChannelId || ''}
                    onChange={handleChange}
                    className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                    placeholder="Enter Discord Voice Channel ID (optional)"
                  />
                  <p className="text-slate-400 text-sm mt-1">
                    Users in this voice channel will not accumulate voice activity time. Leave empty to track all channels.
                  </p>
                </div>
                
                <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-3 text-sm text-blue-200">
                  <p className="font-medium flex items-center">
                    <HiOutlineInformationCircle className="mr-1.5" size={16} />
                    How to find Discord Voice Channel IDs
                  </p>
                  <p className="mt-1 text-blue-100/80">
                    In Discord, enable Developer Mode in Settings → Advanced, then right-click on a voice channel and select "Copy ID".
                  </p>
                </div>
              </div>
            </div>
            
            {/* Counting Game Settings Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
              <div className="flex items-center mb-4">
                <HiOutlineCalculator className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Counting Game Settings
                </h2>
              </div>
              
              <p className="text-slate-300 mb-4">Configure the collaborative counting game where users count up from 1.</p>
              
              <div className="space-y-6">
                <div className="relative flex items-center mt-2">
                  <div className="flex items-center h-5">
                    <input
                      type="checkbox"
                      id="countingGameEnabled"
                      name="countingGameEnabled"
                      checked={settings.countingGameEnabled}
                      onChange={handleChange}
                      className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
                    />
                  </div>
                  <div className="ml-3 text-white">
                    <label htmlFor="countingGameEnabled" className="text-base font-medium flex items-center">
                      Enable Counting Game
                      <div className="group relative ml-2">
                        <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={18} />
                        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                          Users collaborate to count up from 1. Wrong numbers or consecutive counts reset the game and cost lives.
                        </div>
                      </div>
                    </label>
                    <p className="text-slate-400 text-sm mt-1">Enable the collaborative counting game</p>
                  </div>
                </div>
                
                <div className="space-y-4">
                  <div>
                    <label htmlFor="countingChannelId" className="block text-sm font-medium text-slate-300 mb-2">
                      Counting Channel ID
                    </label>
                    <input
                      type="text"
                      id="countingChannelId"
                      name="countingChannelId"
                      value={settings.countingChannelId || ''}
                      onChange={handleChange}
                      className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                      placeholder="Enter Discord Channel ID"
                    />
                    <p className="text-slate-400 text-sm mt-1">
                      The Discord channel where the counting game takes place
                    </p>
                  </div>
                  
                  <div>
                    <label htmlFor="countingTimeoutRoleId" className="block text-sm font-medium text-slate-300 mb-2">
                      Timeout Role ID
                    </label>
                    <input
                      type="text"
                      id="countingTimeoutRoleId"
                      name="countingTimeoutRoleId"
                      value={settings.countingTimeoutRoleId || ''}
                      onChange={handleChange}
                      className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                      placeholder="Enter Discord Role ID"
                    />
                    <p className="text-slate-400 text-sm mt-1">
                      Role assigned to users who lose all lives (should prevent access to counting channel)
                    </p>
                  </div>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label htmlFor="creditsPerCount" className="block text-sm font-medium text-slate-300 mb-2">
                        Credits per Successful Count
                      </label>
                      <input
                        type="number"
                        id="creditsPerCount"
                        name="creditsPerCount"
                        value={settings.creditsPerCount || ''}
                        onChange={handleChange}
                        min="1"
                        className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                      />
                      <p className="text-slate-400 text-sm mt-1">Credits awarded for each correct number</p>
                    </div>
                    
                    <div>
                      <label htmlFor="countingLives" className="block text-sm font-medium text-slate-300 mb-2">
                        Lives per User
                      </label>
                      <input
                        type="number"
                        id="countingLives"
                        name="countingLives"
                        value={settings.countingLives || ''}
                        onChange={handleChange}
                        min="1"
                        max="10"
                        className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                      />
                      <p className="text-slate-400 text-sm mt-1">Number of mistakes allowed before timeout</p>
                    </div>
                  </div>
                </div>
                
                <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4 text-sm text-blue-200">
                  <h4 className="font-medium flex items-center mb-2">
                    <HiOutlineInformationCircle className="mr-1.5" size={16} />
                    How the Counting Game Works
                  </h4>
                  <ul className="list-disc list-inside space-y-1 ml-1 text-blue-100/80">
                    <li>Users collaborate to count from 1 upward in the designated channel</li>
                    <li>Each correct number awards credits to the user</li>
                    <li>Wrong numbers or counting twice in a row resets the count and costs a life</li>
                    <li>Users who lose all lives get timed out with progressive durations (24h, 48h, 72h...)</li>
                    <li>The timeout role should be configured to prevent access to the counting channel</li>
                  </ul>
                </div>
              </div>
            </div>
            
            {/* Save Button */}
            <div className="flex justify-end mb-8">
              <button
                type="submit"
                disabled={isLoading || isSaving}
                className={`px-6 py-3 bg-primary text-white font-semibold rounded-md shadow-lg transition-all duration-300 flex items-center ${
                  (isLoading || isSaving) 
                    ? 'opacity-50 cursor-not-allowed' 
                    : 'hover:bg-primary/90 hover:shadow-primary/20 transform hover:-translate-y-0.5'
                }`}
              >
                {isSaving ? (
                  <>
                    <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2"></div>
                    Saving Settings...
                  </>
                ) : (
                  <>
                    <HiOutlineCheck className="mr-2" size={20} />
                    Save Settings
                  </>
                )}
              </button>
            </div>
            
            {/* Level Progression Estimator Card */}
            <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6">
              <div className="flex items-center mb-4">
                <HiOutlineCalculator className="text-primary mr-3" size={24} />
                <h2 className="text-xl font-semibold text-white">
                  Level Progression Estimator
                </h2>
              </div>
              
              <p className="text-slate-300 mb-4">
                Estimate time and messages needed to reach a target level based on current settings.
              </p>
              
              <div className="space-y-6">
                <div className="space-y-3">
                  <label htmlFor="targetLevel" className="block text-white font-medium">
                    Target Level
                  </label>
                  <div className="flex items-center gap-4">
                    <input
                      id="targetLevel"
                      type="number"
                      min="2"
                      value={targetLevel || ''}
                      onChange={(e) => {
                        const val = e.target.value === '' ? 2 : parseInt(e.target.value);
                        setTargetLevel(isNaN(val) ? 2 : val);
                      }}
                      className="bg-slate-800 text-white border border-slate-700 rounded-lg py-3 px-4 w-full focus:outline-none focus:ring-2 focus:ring-primary/50"
                      placeholder="Enter a level number..."
                    />
                    <button
                      onClick={calculateLevelProgression}
                      className="px-5 py-3 bg-primary/80 hover:bg-primary text-white font-semibold rounded-lg transition-colors flex items-center whitespace-nowrap"
                    >
                      <HiOutlineCalculator className="mr-2" size={18} />
                      Calculate
                    </button>
                  </div>
                </div>
                
                {estimationResults && (
                  <motion.div 
                    className="bg-slate-800/70 rounded-lg p-5 border border-white/5"
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3 }}
                  >
                    <h3 className="text-lg font-semibold text-white mb-3 flex items-center">
                      <HiOutlineBadgeCheck className="mr-2 text-primary" size={22} />
                      Results for Level {targetLevel}
                    </h3>
                    
                    <div className="space-y-5">
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="bg-slate-800/80 rounded-lg p-3">
                          <p className="text-sm font-medium text-slate-400">Total XP Required</p>
                          <p className="text-xl font-semibold text-white">{estimationResults.totalXpRequired.toLocaleString()} XP</p>
                        </div>
                        
                        <div className="bg-slate-800/80 rounded-lg p-3">
                          <p className="text-sm font-medium text-slate-400">Messages Needed</p>
                          <p className="text-xl font-semibold text-white">{estimationResults.messagesNeeded.toLocaleString()}</p>
                        </div>
                      </div>
                      
                      <div className="bg-slate-800/80 rounded-lg divide-y divide-slate-700/50">
                        <div className="p-3">
                          <p className="text-sm font-medium text-emerald-400 flex items-center">
                            <FiActivity className="mr-1.5" size={16} />
                            Perfect-case Timeline
                          </p>
                          <p className="text-white font-medium mt-1">{estimationResults.timeNeeded}</p>
                          <p className="text-xs text-slate-400 italic">Assumes messages sent exactly at cooldown rate</p>
                        </div>
                        
                        <div className="p-3">
                          <p className="text-sm font-medium text-yellow-400 flex items-center">
                            <FiUsers className="mr-1.5" size={16} />
                            Realistic Timeline
                          </p>
                          <p className="text-white font-medium mt-1">{estimationResults.realisticTimeNeeded}</p>
                          <p className="text-xs text-slate-400 italic">Based on ~2 hours of daily activity</p>
                        </div>
                      </div>
                      
                      <div className="bg-slate-800/80 rounded-lg p-3">
                        <p className="text-sm font-medium text-slate-400">Credits from Leveling</p>
                        <p className="text-xl font-semibold text-white">{estimationResults.creditsEarned.toLocaleString()} credits</p>
                      </div>
                    </div>
                  </motion.div>
                )}
                
                <div className="bg-slate-800/30 rounded-lg p-4 text-sm text-slate-400">
                  <h4 className="font-medium text-white mb-2 flex items-center">
                    <HiOutlineInformationCircle className="mr-1.5" size={16} />
                    How the Level Formula Works
                  </h4>
                  <ul className="list-disc list-inside space-y-1 ml-1">
                    <li>XP formula: <span className="font-mono bg-slate-800 px-1.5 py-0.5 rounded text-xs">baseXp + (levelMultiplier × level^levelExponent ÷ levelFactor)</span></li>
                    <li>Time estimate assumes message cooldown of {settings.cooldownSeconds} seconds</li>
                    <li>Users earn {settings.xpToAward} XP per message and {settings.creditsPerLevel} credits per level</li>
                    <li>Discord roles are automatically assigned at levels 5, 15, 30, 40, 50, 70, and 100</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}

export default DiscordBotSettings; 