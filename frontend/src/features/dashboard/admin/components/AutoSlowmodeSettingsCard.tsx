import React, { ChangeEvent } from 'react';
import { HiOutlineShieldCheck, HiOutlineInformationCircle, HiOutlineCheck } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface SlowmodeStatus {
  [channelId: string]: number;
}

interface AutoSlowmodeSettingsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
  slowmodeStatus: SlowmodeStatus;
  fetchSlowmodeStatus: () => void;
  isLoadingSlowmode: boolean;
}

export const AutoSlowmodeSettingsCard = ({
  settings,
  handleChange,
  slowmodeStatus,
  fetchSlowmodeStatus,
  isLoadingSlowmode,
}: AutoSlowmodeSettingsCardProps) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <HiOutlineShieldCheck className="text-primary mr-3" size={24} />
        <h2 className="text-xl font-semibold text-white">
          Auto Slowmode Settings
        </h2>
      </div>

      <p className="text-slate-300 mb-4">
        Automatically apply slowmode to channels when activity exceeds thresholds.
      </p>

      <div className="space-y-6">
        <div className="relative flex items-center mt-2">
          <div className="flex items-center h-5">
            <input
              type="checkbox"
              id="autoSlowmodeEnabled"
              name="autoSlowmodeEnabled"
              checked={settings.autoSlowmodeEnabled}
              onChange={handleChange}
              className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
            />
          </div>
          <div className="ml-3 text-white">
            <label htmlFor="autoSlowmodeEnabled" className="text-base font-medium flex items-center">
              Enable Auto Slowmode
              <div className="group relative ml-2">
                <HiOutlineInformationCircle
                  className="text-slate-400 hover:text-primary cursor-help transition-colors"
                  size={18}
                />
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                  Automatically apply slowmode when message activity in monitored channels exceeds the
                  threshold.
                </div>
              </div>
            </label>
            <p className="text-slate-400 text-sm mt-1">
              Monitor channels and apply slowmode during high activity
            </p>
          </div>
        </div>

        <div className="space-y-4">
          <div>
            <label htmlFor="slowmodeChannelIds" className="block text-sm font-medium text-slate-300 mb-2">
              Monitored Channel IDs
            </label>
            <input
              type="text"
              id="slowmodeChannelIds"
              name="slowmodeChannelIds"
              value={settings.slowmodeChannelIds || ''}
              onChange={handleChange}
              className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              placeholder="Enter Discord Channel IDs separated by commas"
            />
            <p className="text-slate-400 text-sm mt-1">
              Comma-separated list of Discord channel IDs to monitor for auto slowmode (e.g.,
              123456789,987654321)
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label htmlFor="activityThreshold" className="block text-sm font-medium text-slate-300 mb-2">
                Activity Threshold
              </label>
              <input
                type="number"
                id="activityThreshold"
                name="activityThreshold"
                value={settings.activityThreshold || ''}
                onChange={handleChange}
                min="1"
                className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              />
              <p className="text-slate-400 text-sm mt-1">
                Messages per time window to trigger slowmode
              </p>
            </div>

            <div>
              <label htmlFor="slowmodeTimeWindow" className="block text-sm font-medium text-slate-300 mb-2">
                Time Window (minutes)
              </label>
              <input
                type="number"
                id="slowmodeTimeWindow"
                name="slowmodeTimeWindow"
                value={settings.slowmodeTimeWindow || ''}
                onChange={handleChange}
                min="1"
                className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              />
              <p className="text-slate-400 text-sm mt-1">Period to monitor for message activity</p>
            </div>

            <div>
              <label htmlFor="slowmodeDuration" className="block text-sm font-medium text-slate-300 mb-2">
                Slowmode Duration (seconds)
              </label>
              <input
                type="number"
                id="slowmodeDuration"
                name="slowmodeDuration"
                value={settings.slowmodeDuration || ''}
                onChange={handleChange}
                min="0"
                max="21600"
                className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              />
              <p className="text-slate-400 text-sm mt-1">
                Slowmode duration to apply (0-21600 seconds)
              </p>
            </div>

            <div>
              <label htmlFor="slowmodeCooldown" className="block text-sm font-medium text-slate-300 mb-2">
                Cooldown (minutes)
              </label>
              <input
                type="number"
                id="slowmodeCooldown"
                name="slowmodeCooldown"
                value={settings.slowmodeCooldown || ''}
                onChange={handleChange}
                min="1"
                className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              />
              <p className="text-slate-400 text-sm mt-1">Minimum time before re-applying slowmode</p>
            </div>
          </div>
        </div>

        {Object.keys(slowmodeStatus).length > 0 && (
          <div className="bg-slate-800/60 rounded-lg p-4 border border-slate-600/50">
            <div className="flex items-center justify-between mb-3">
              <h4 className="font-medium text-white flex items-center">
                <HiOutlineShieldCheck className="mr-1.5 text-primary" size={16} />
                Active Slowmodes
              </h4>
              <button
                onClick={fetchSlowmodeStatus}
                disabled={isLoadingSlowmode}
                className="px-2 py-1 bg-slate-700 hover:bg-slate-600 text-white text-xs rounded transition-colors flex items-center"
              >
                {isLoadingSlowmode ? (
                  <div className="w-3 h-3 border border-white border-t-transparent rounded-full animate-spin mr-1"></div>
                ) : (
                  <HiOutlineCheck className="mr-1" size={12} />
                )}
                Refresh
              </button>
            </div>
            <div className="space-y-2">
              {Object.entries(slowmodeStatus).map(([channelId, duration]) => (
                <div
                  key={channelId}
                  className="flex items-center justify-between bg-slate-700/50 rounded px-3 py-2"
                >
                  <span className="text-slate-300 font-mono text-sm">{channelId}</span>
                  <span className="text-primary font-medium">{duration}s slowmode</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4 text-sm text-blue-200">
          <h4 className="font-medium flex items-center mb-2">
            <HiOutlineInformationCircle className="mr-1.5" size={16} />
            How Auto Slowmode Works
          </h4>
          <ul className="list-disc list-inside space-y-1 ml-1 text-blue-100/80">
            <li>Monitors message activity in configured channels in real-time</li>
            <li>Applies slowmode when activity exceeds the threshold within the time window</li>
            <li>Respects cooldown periods to prevent rapid slowmode changes</li>
            <li>Requires bot to have "Manage Channel" permissions in monitored channels</li>
            <li>Use 0 seconds for slowmode duration to disable slowmode</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default React.memo(AutoSlowmodeSettingsCard); 