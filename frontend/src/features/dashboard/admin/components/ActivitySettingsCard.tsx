import React, { ChangeEvent } from 'react';
import { HiOutlineChat, HiOutlineInformationCircle, HiOutlineClock } from 'react-icons/hi';
import { FiActivity } from 'react-icons/fi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface ActivitySettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const ActivitySettingsCard = ({ settings, handleChange }: ActivitySettingsCardProps) => {
  return (
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
  );
};

export default React.memo(ActivitySettingsCard); 