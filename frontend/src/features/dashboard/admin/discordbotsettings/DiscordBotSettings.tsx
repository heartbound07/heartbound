import { useState, useEffect, ChangeEvent } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';
import { 
  HiOutlineCheck, 
  HiOutlineInformationCircle, 
} from 'react-icons/hi';
import { FiSettings } from 'react-icons/fi';
import { motion } from 'framer-motion';
import { SelfAssignableRolesSettings } from './SelfAssignableRolesSettings';
import { CreditDropSettings } from './CreditDropSettings';
import { useDiscordBotSettings } from '@/hooks/useDiscordBotSettings';
import { ActivitySettingsCard } from './ActivitySettingsCard';
import { LevelingSettingsCard } from './LevelingSettingsCard';
import { FishingSettingsCard } from './FishingSettingsCard';
import { CountingGameSettingsCard } from './CountingGameSettingsCard';
import { AutoSlowmodeSettingsCard } from './AutoSlowmodeSettingsCard';
import { TimedOutUsersCard } from './TimedOutUsersCard';
import { LevelProgressionEstimatorCard } from './LevelProgressionEstimatorCard';
import { LevelRoleRewardsCard } from './LevelRoleRewardsCard';
import { RoleMultipliersCard } from './RoleMultipliersCard';
import { VoiceActivitySettingsCard } from './VoiceActivitySettingsCard';

interface ToastNotification {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface TimedOutUser {
  userId: string;
  username: string;
  avatar: string;
  timeoutLevel: number;
  timeoutExpiry: string;
  livesRemaining: number;
  totalCorrectCounts: number;
  totalMistakes: number;
  bestCount: number;
  timeoutHoursRemaining: number;
  timeoutDuration: string;
}

interface SlowmodeStatus {
  [channelId: string]: number; // channelId -> slowmode duration in seconds
}

const calculateRequiredXp = (level: number, baseXp: number, levelMultiplier: number, levelExponent: number, levelFactor: number): number => {
  return Math.floor(baseXp + (levelMultiplier * Math.pow(level, levelExponent) / levelFactor));
};

/**
 * Parse role multipliers string and return a readable format
 */
const parseRoleMultipliers = (roleMultipliers: string): Array<{ roleId: string; multiplier: string }> => {
  if (!roleMultipliers || roleMultipliers.trim() === '') {
    return [];
  }
  
  try {
    return roleMultipliers.split(',').map(entry => {
      const [roleId, multiplier] = entry.trim().split(':');
      return { roleId: roleId?.trim() || '', multiplier: multiplier?.trim() || '' };
    }).filter(entry => entry.roleId && entry.multiplier);
  } catch (error) {
    console.error('Error parsing role multipliers:', error);
    return [];
  }
};

export function DiscordBotSettings() {
  const { hasRole } = useAuth();
  const { settings, isLoading, error: settingsError, updateField } = useDiscordBotSettings();
  const [isSaving, setIsSaving] = useState(false);
  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [targetLevel, setTargetLevel] = useState<number>(10);
  const [estimationResults, setEstimationResults] = useState<{
    messagesNeeded: number;
    timeNeeded: string;
    totalXpRequired: number;
    creditsEarned: number;
    realisticTimeNeeded: string;
  } | null>(null);
  const [timedOutUsers, setTimedOutUsers] = useState<TimedOutUser[]>([]);
  const [isLoadingTimeouts, setIsLoadingTimeouts] = useState(false);
  const [removingTimeouts, setRemovingTimeouts] = useState<Set<string>>(new Set());
  const [slowmodeStatus, setSlowmodeStatus] = useState<SlowmodeStatus>({});
  const [isLoadingSlowmode, setIsLoadingSlowmode] = useState(false);
  
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
  
  // Fetch timed out users
  const fetchTimedOutUsers = async () => {
    try {
      setIsLoadingTimeouts(true);
      const response = await httpClient.get('/admin/discord/counting/timeouts');
      setTimedOutUsers(response.data);
    } catch (err: any) {
      console.error('Failed to fetch timed out users:', err);
      const errorMessage = err.response?.data?.message || 'Failed to load timed out users';
      showToast(errorMessage, 'error');
    } finally {
      setIsLoadingTimeouts(false);
    }
  };

  // Fetch slowmode status
  const fetchSlowmodeStatus = async () => {
    try {
      setIsLoadingSlowmode(true);
      const response = await httpClient.get('/admin/discord/slowmode/status');
      setSlowmodeStatus(response.data);
    } catch (err) {
      console.error('Failed to fetch slowmode status:', err);
      // Don't show toast for this as it's not critical
    } finally {
      setIsLoadingSlowmode(false);
    }
  };

  // Remove user timeout
  const removeUserTimeout = async (userId: string) => {
    try {
      setRemovingTimeouts(prev => new Set(prev).add(userId));
      
      await httpClient.delete(`/admin/discord/counting/timeouts/${userId}`);
      
      // Remove from local state
      setTimedOutUsers(prev => prev.filter(user => user.userId !== userId));
      showToast('User timeout removed successfully', 'success');
    } catch (err: any) {
      console.error('Failed to remove user timeout:', err);
      const errorMessage = err.response?.data?.message || 'Failed to remove user timeout';
      showToast(errorMessage, 'error');
    } finally {
      setRemovingTimeouts(prev => {
        const newSet = new Set(prev);
        newSet.delete(userId);
        return newSet;
      });
    }
  };
  
  useEffect(() => {
    fetchTimedOutUsers();
    fetchSlowmodeStatus();
  }, []);
  
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const { name, value, type } = e.target;

    // Restore validation for role ID and channel ID fields
    if (name.endsWith('RoleId') || name.endsWith('ChannelId')) {
      if (name === 'slowmodeChannelIds') {
        if (value === '' || /^(\d+)(,\s*\d+)*$/.test(value)) {
          updateField(name, value);
        }
        return;
      }
      
      if (value === '' || /^\d+$/.test(value)) {
        updateField(name, value);
      }
      return;
    }

    // Restore validation for role multipliers field
    if (name === 'roleMultipliers') {
      if (value === '' || /^[\d:.,\s]*$/.test(value)) {
        updateField(name, value);
      }
      return;
    }
    
    if (type === 'checkbox') {
      updateField(name, e.target.checked);
    } else if (type === 'number') {
      const numValue = value === '' ? 0 : parseInt(value, 10);
      updateField(name, isNaN(numValue) ? 0 : numValue);
    } else {
      updateField(name, value);
    }
  };
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setIsSaving(true);
      
      await httpClient.put('/admin/discord/settings', settings);
      
      showToast('Discord bot settings updated successfully', 'success');
    } catch (err: any) {
      console.error('Failed to update Discord bot settings:', err);
      const errorMessage = err.response?.data?.message || 'Failed to update Discord bot settings';
      showToast(errorMessage, 'error');
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
    
    if (!settings.xpToAward || settings.xpToAward <= 0) {
        showToast('XP to Award must be greater than 0.', 'error');
        return;
    }

    if (!settings.cooldownSeconds || settings.cooldownSeconds <= 0) {
        showToast('Cooldown must be greater than 0.', 'error');
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
    const messagesNeeded = Math.ceil(totalXpRequired / settings.xpToAward);
    
    // Perfect-case time calculation (assuming messages sent at exact cooldown)
    const timeInSeconds = messagesNeeded * settings.cooldownSeconds;
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
      {settingsError && (
        <motion.div 
          className="mb-6 bg-red-900/40 border border-red-500/50 rounded-lg p-4 text-red-200"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <p className="flex items-center">
            <HiOutlineInformationCircle className="mr-2 flex-shrink-0" size={20} />
            {settingsError}
          </p>
        </motion.div>
      )}
      
      <form onSubmit={handleSubmit}>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left Column - Activity Settings */}
          <div>
            {/* Activity Credits Settings Card */}
            <ActivitySettingsCard settings={settings} handleChange={handleChange} />
            
            {/* Leveling System Card */}
            <LevelingSettingsCard settings={settings} handleChange={handleChange} />
            
            {/* Fishing Game Settings Card */}
            <FishingSettingsCard settings={settings} handleChange={handleChange} />
            
            {/* Credit Drop Settings Card */}
            <CreditDropSettings settings={settings} handleChange={handleChange} />
            
            {/* Counting Game Settings Card */}
            <CountingGameSettingsCard settings={settings} handleChange={handleChange} />
            
            {/* Auto Slowmode Settings Card */}
            <AutoSlowmodeSettingsCard
              settings={settings}
              handleChange={handleChange}
              slowmodeStatus={slowmodeStatus}
              fetchSlowmodeStatus={fetchSlowmodeStatus}
              isLoadingSlowmode={isLoadingSlowmode}
            />
            
            {/* Timed Out Users Management Card */}
            <TimedOutUsersCard
              timedOutUsers={timedOutUsers}
              isLoadingTimeouts={isLoadingTimeouts}
              removingTimeouts={removingTimeouts}
              fetchTimedOutUsers={fetchTimedOutUsers}
              removeUserTimeout={removeUserTimeout}
            />
            
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
            <LevelProgressionEstimatorCard
              settings={settings}
              calculateLevelProgression={() => calculateLevelProgression()}
              estimationResults={estimationResults}
              targetLevel={targetLevel}
              setTargetLevel={setTargetLevel}
            />
          </div>
          
          {/* Right Column - Role Rewards and Calculator */}
          <div>
            {/* Self-Assignable Roles Card */}
            <SelfAssignableRolesSettings settings={settings} handleChange={handleChange} />

            {/* Level Role Rewards Card */}
            <LevelRoleRewardsCard settings={settings} handleChange={handleChange} />
            
            {/* Role Multipliers Configuration Card */}
            <RoleMultipliersCard
              settings={settings}
              handleChange={handleChange}
              parseRoleMultipliers={parseRoleMultipliers}
            />
            
            {/* Voice Activity Settings Card */}
            <VoiceActivitySettingsCard settings={settings} handleChange={handleChange} />
          </div>
        </div>
      </form>
    </div>
  );
}

export default DiscordBotSettings; 