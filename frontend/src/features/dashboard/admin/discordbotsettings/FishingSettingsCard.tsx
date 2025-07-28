import React, { ChangeEvent } from 'react';
import { HiOutlineInformationCircle } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface FishingSettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const FishingSettingsCard = ({ settings, handleChange }: FishingSettingsCardProps) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <span className="text-primary mr-3 text-2xl">🎣</span>
        <h2 className="text-xl font-semibold text-white">
          Fishing Game Settings
        </h2>
      </div>
      
      <p className="text-slate-300 mb-4">Configure limits and anti-botting measures for the /fish command.</p>
      
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <label htmlFor="fishingMinCatches" className="block text-white">
              Minimum Catches per Session
            </label>
            <input
              type="number"
              id="fishingMinCatches"
              name="fishingMinCatches"
              value={settings.fishingMinCatches || ''}
              onChange={handleChange}
              min="1"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">The minimum fish a user can catch before a cooldown.</p>
          </div>

          <div className="space-y-2">
            <label htmlFor="fishingMaxCatches" className="block text-white">
              Maximum Catches per Session
            </label>
            <input
              type="number"
              id="fishingMaxCatches"
              name="fishingMaxCatches"
              value={settings.fishingMaxCatches || ''}
              onChange={handleChange}
              min="1"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">The maximum fish a user can catch before a cooldown.</p>
          </div>
          
          <div className="space-y-2">
            <label htmlFor="fishingDefaultMaxCatches" className="block text-white">
              Fallback Max Catches
            </label>
            <input
              type="number"
              id="fishingDefaultMaxCatches"
              name="fishingDefaultMaxCatches"
              value={settings.fishingDefaultMaxCatches || ''}
              onChange={handleChange}
              min="1"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">Default limit for users who don't have a random limit set yet.</p>
          </div>

          <div className="space-y-2">
            <label htmlFor="fishingCooldownHours" className="block text-white">
              Cooldown Duration (Hours)
            </label>
            <input
              type="number"
              id="fishingCooldownHours"
              name="fishingCooldownHours"
              value={settings.fishingCooldownHours || ''}
              onChange={handleChange}
              min="1"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">Hours users must wait after reaching limit</p>
          </div>
          
          <div className="space-y-2">
            <label htmlFor="fishingLimitWarningThreshold" className="block text-white">
              Warning Threshold
            </label>
            <input
              type="number"
              id="fishingLimitWarningThreshold"
              name="fishingLimitWarningThreshold"
              value={settings.fishingLimitWarningThreshold || ''}
              onChange={handleChange}
              min="0"
              max="1"
              step="0.1"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">Fraction of limit to trigger warning (0.9 = 90%)</p>
          </div>
          
          <div className="space-y-2">
            <label htmlFor="fishingPenaltyCredits" className="block text-white">
              Cooldown Penalty Credits
            </label>
            <input
              type="number"
              id="fishingPenaltyCredits"
              name="fishingPenaltyCredits"
              value={settings.fishingPenaltyCredits || ''}
              onChange={handleChange}
              min="0"
              className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-md text-white focus:border-primary focus:ring-1 focus:ring-primary"
            />
            <p className="text-slate-400 text-sm">Credits deducted for fishing during cooldown (silent penalty)</p>
          </div>
        </div>
        
        <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3 text-sm text-yellow-200">
          <p className="font-medium flex items-center">
            <HiOutlineInformationCircle className="mr-1.5" size={16} />
            Anti-Botting Features
          </p>
          <ul className="mt-1 text-yellow-100/80 space-y-1 list-disc list-inside">
            <li>A user's fishing limit is randomized between the min and max values each time their cooldown resets.</li>
            <li>Users who try to fish during cooldown silently lose credits as a penalty</li>
            <li>The penalty is not shown to the user to prevent gaming the system</li>
            <li>All fishing activities are logged for administrative review</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default React.memo(FishingSettingsCard); 