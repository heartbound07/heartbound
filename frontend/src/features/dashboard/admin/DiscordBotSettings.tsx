import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import httpClient from '@/lib/api/httpClient';
import { Toast } from '@/components/Toast';

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
  creditsPerLevel: 50
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
    const { name, value, type, checked } = e.target;
    
    setSettings(prevSettings => ({
      ...prevSettings,
      [name]: type === 'checkbox' ? checked : type === 'number' ? parseInt(value, 10) : value
    }));
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
    const messagesNeeded = Math.ceil(totalXpRequired / (settings.xpToAward || 1)); // Prevent division by zero
    
    // Calculate minimum time needed (perfect scenario with no delays beyond cooldown)
    const timeInSeconds = messagesNeeded * (settings.cooldownSeconds || 1); // Prevent multiplication by zero
    const days = Math.floor(timeInSeconds / 86400);
    const hours = Math.floor((timeInSeconds % 86400) / 3600);
    const minutes = Math.floor((timeInSeconds % 3600) / 60);
    
    // Calculate credits earned from leveling
    const creditsEarned = (targetLevel - 1) * (settings.creditsPerLevel || 0);
    
    const timeNeeded = days > 0 
      ? `${days} days, ${hours} hours, ${minutes} minutes` 
      : hours > 0 
        ? `${hours} hours, ${minutes} minutes` 
        : `${minutes} minutes`;
        
    setEstimationResults({
      messagesNeeded,
      timeNeeded,
      totalXpRequired,
      creditsEarned
    });
  };
  
  return (
    <div className="container mx-auto p-6">
      {/* Toast notifications */}
      <div className="fixed top-4 right-4 z-50 space-y-2">
        {toasts.map(toast => (
          <Toast
            key={toast.id}
            message={toast.message}
            type={toast.type}
            onClose={() => removeToast(toast.id)}
          />
        ))}
      </div>

      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-white">Discord Bot Settings</h1>
          {isLoading && <div className="text-primary animate-pulse">Loading...</div>}
        </div>
        
        {error && (
          <div className="bg-red-900/50 text-red-200 p-4 rounded-lg mb-6 border border-red-700">
            {error}
          </div>
        )}
        
        <form onSubmit={handleSubmit}>
          {/* Chat Activity Settings Section */}
          <div className="mb-8">
            <h2 className="text-xl font-semibold text-white mb-4 border-b border-white/10 pb-2">
              Chat Activity Settings
            </h2>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <label className="flex items-center gap-2 text-white">
                  <input
                    type="checkbox"
                    id="activityEnabled"
                    name="activityEnabled"
                    checked={settings.activityEnabled}
                    onChange={handleChange}
                    className="rounded border-slate-700 bg-slate-800 text-primary focus:ring-primary focus:ring-offset-slate-900"
                  />
                  Enable Chat Activity Rewards
                </label>
                <p className="text-slate-400 text-sm">When enabled, users earn credits for being active in Discord chat</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="creditsToAward" className="block text-white">
                  Credits to Award
                </label>
                <input
                  type="number"
                  id="creditsToAward"
                  name="creditsToAward"
                  value={settings.creditsToAward}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Credits awarded when a user meets the message threshold</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="messageThreshold" className="block text-white">
                  Message Threshold
                </label>
                <input
                  type="number"
                  id="messageThreshold"
                  name="messageThreshold"
                  value={settings.messageThreshold}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Number of messages required within the time window to earn credits</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="timeWindowMinutes" className="block text-white">
                  Time Window (minutes)
                </label>
                <input
                  type="number"
                  id="timeWindowMinutes"
                  name="timeWindowMinutes"
                  value={settings.timeWindowMinutes}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Time period in which messages are counted for the threshold</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="cooldownSeconds" className="block text-white">
                  Cooldown (seconds)
                </label>
                <input
                  type="number"
                  id="cooldownSeconds"
                  name="cooldownSeconds"
                  value={settings.cooldownSeconds}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Cooldown between counting messages from the same user</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="minMessageLength" className="block text-white">
                  Minimum Message Length
                </label>
                <input
                  type="number"
                  id="minMessageLength"
                  name="minMessageLength"
                  value={settings.minMessageLength}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Minimum character length for a message to count</p>
              </div>
            </div>
          </div>
          
          {/* Leveling System Settings Section */}
          <div className="mb-8">
            <h2 className="text-xl font-semibold text-white mb-4 border-b border-white/10 pb-2">
              Leveling System Settings
            </h2>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <label className="flex items-center gap-2 text-white">
                  <input
                    type="checkbox"
                    id="levelingEnabled"
                    name="levelingEnabled"
                    checked={settings.levelingEnabled}
                    onChange={handleChange}
                    className="rounded border-slate-700 bg-slate-800 text-primary focus:ring-primary focus:ring-offset-slate-900"
                  />
                  Enable Leveling System
                </label>
                <p className="text-slate-400 text-sm">When enabled, users earn XP and level up for being active in Discord</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="xpToAward" className="block text-white">
                  XP to Award per Message
                </label>
                <input
                  type="number"
                  id="xpToAward"
                  name="xpToAward"
                  value={settings.xpToAward}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">XP awarded for each valid message</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="baseXp" className="block text-white">
                  Base XP
                </label>
                <input
                  type="number"
                  id="baseXp"
                  name="baseXp"
                  value={settings.baseXp}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Base XP required for the first level</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="levelMultiplier" className="block text-white">
                  Level Multiplier
                </label>
                <input
                  type="number"
                  id="levelMultiplier"
                  name="levelMultiplier"
                  value={settings.levelMultiplier}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Multiplier used in level XP calculation</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="levelExponent" className="block text-white">
                  Level Exponent
                </label>
                <input
                  type="number"
                  id="levelExponent"
                  name="levelExponent"
                  value={settings.levelExponent}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Exponent for level scaling</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="levelFactor" className="block text-white">
                  Level Factor
                </label>
                <input
                  type="number"
                  id="levelFactor"
                  name="levelFactor"
                  value={settings.levelFactor}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Additional factor for level scaling</p>
              </div>
              
              <div className="space-y-2">
                <label htmlFor="creditsPerLevel" className="block text-white">
                  Credits per Level Up
                </label>
                <input
                  type="number"
                  id="creditsPerLevel"
                  name="creditsPerLevel"
                  value={settings.creditsPerLevel}
                  onChange={handleChange}
                  min="1"
                  className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
                />
                <p className="text-slate-400 text-sm">Credits awarded when a user levels up</p>
              </div>
            </div>
          </div>
          
          {/* Save Button */}
          <div className="flex justify-end mt-6">
            <button
              type="submit"
              disabled={isLoading || isSaving}
              className={`px-6 py-3 bg-primary/80 hover:bg-primary text-white font-semibold rounded-md transition-colors ${
                (isLoading || isSaving) ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {isSaving ? 'Saving...' : 'Save Settings'}
            </button>
          </div>
        </form>
      </div>
      
      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10 mt-8">
        <h2 className="text-xl font-bold text-white mb-4">Level Progression Estimator</h2>
        <p className="text-slate-300 mb-6">Estimate time and messages needed to reach a target level based on current settings.</p>
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          <div>
            <label htmlFor="targetLevel" className="block text-sm font-medium text-slate-300 mb-2">
              Target Level
            </label>
            <div className="flex items-center">
              <input
                id="targetLevel"
                type="number"
                min="2"
                value={targetLevel || ''}
                onChange={(e) => {
                  const val = e.target.value === '' ? 2 : parseInt(e.target.value);
                  setTargetLevel(isNaN(val) ? 2 : val);
                }}
                className="bg-slate-800 text-white border border-slate-700 rounded-md py-2 px-3 w-full focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
              <button
                onClick={calculateLevelProgression}
                className="ml-4 px-4 py-2 bg-primary/80 hover:bg-primary text-white font-semibold rounded-md transition-colors"
              >
                Calculate
              </button>
            </div>
          </div>
          
          {estimationResults && (
            <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5">
              <h3 className="text-lg font-semibold text-white mb-3">Results</h3>
              <div className="space-y-2 text-slate-300">
                <p><span className="font-medium">Total XP Required:</span> {estimationResults.totalXpRequired.toLocaleString()} XP</p>
                <p><span className="font-medium">Messages Needed:</span> {estimationResults.messagesNeeded.toLocaleString()} messages</p>
                <p><span className="font-medium">Minimum Time Needed:</span> {estimationResults.timeNeeded}</p>
                <p><span className="font-medium">Credits from Leveling:</span> {estimationResults.creditsEarned.toLocaleString()} credits</p>
                <p className="text-xs text-slate-400 italic mt-2">Note: This is a perfect-case estimate assuming consistent messaging at the cooldown rate.</p>
              </div>
            </div>
          )}
        </div>
        
        <div className="mt-4 text-slate-400 text-sm">
          <h3 className="font-semibold text-slate-300 mb-2">How this works:</h3>
          <ul className="list-disc list-inside space-y-1">
            <li>Uses the XP formula: baseXp + (levelMultiplier × level^levelExponent ÷ levelFactor)</li>
            <li>Calculates total XP needed to reach the target level</li>
            <li>Estimates messages needed based on XP per message ({settings.xpToAward} XP)</li>
            <li>Estimates time based on the cooldown between messages ({settings.cooldownSeconds} seconds)</li>
            <li>Different role rewards are given at levels 5, 15, 30, 40, 50, 70, and 100</li>
          </ul>
        </div>
      </div>
    </div>
  );
}

export default DiscordBotSettings; 