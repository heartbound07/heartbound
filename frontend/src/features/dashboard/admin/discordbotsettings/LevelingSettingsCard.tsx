import React, { ChangeEvent } from 'react';
import { HiOutlineChartBar, HiOutlineInformationCircle, HiOutlineLightningBolt, HiOutlineCalculator } from 'react-icons/hi';
import { FiAward } from 'react-icons/fi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface LevelingSettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const LevelingSettingsCard = ({ settings, handleChange }: LevelingSettingsCardProps) => {
  return (
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
  );
};

export default React.memo(LevelingSettingsCard); 