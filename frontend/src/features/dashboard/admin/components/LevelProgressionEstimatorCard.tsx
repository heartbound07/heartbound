import React from 'react';
import { motion } from 'framer-motion';
import { HiOutlineCalculator, HiOutlineBadgeCheck, HiOutlineInformationCircle } from 'react-icons/hi';
import { FiActivity, FiUsers } from 'react-icons/fi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface LevelProgressionEstimatorCardProps {
  settings: DiscordBotSettingsDTO;
  calculateLevelProgression: (targetLevel: number) => void;
  estimationResults: {
    messagesNeeded: number;
    timeNeeded: string;
    totalXpRequired: number;
    creditsEarned: number;
    realisticTimeNeeded: string;
  } | null;
  targetLevel: number;
  setTargetLevel: (level: number) => void;
}

export const LevelProgressionEstimatorCard = ({
  settings,
  calculateLevelProgression,
  estimationResults,
  targetLevel,
  setTargetLevel,
}: LevelProgressionEstimatorCardProps) => {
  return (
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
              onClick={() => calculateLevelProgression(targetLevel)}
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
  );
};

export default React.memo(LevelProgressionEstimatorCard); 