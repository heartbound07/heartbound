import React, { ChangeEvent } from 'react';
import { FiActivity } from 'react-icons/fi';
import { HiOutlineInformationCircle } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface VoiceActivitySettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const VoiceActivitySettingsCard = ({ settings, handleChange }: VoiceActivitySettingsCardProps) => {
  return (
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
  );
};

export default React.memo(VoiceActivitySettingsCard); 