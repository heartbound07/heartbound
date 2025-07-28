import React, { ChangeEvent } from 'react';
import { HiOutlineCalculator, HiOutlineInformationCircle } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface CountingGameSettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const CountingGameSettingsCard = ({ settings, handleChange }: CountingGameSettingsCardProps) => {
  return (
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
  );
};

export default React.memo(CountingGameSettingsCard); 