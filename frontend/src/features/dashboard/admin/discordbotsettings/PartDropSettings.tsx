import { GiBigGear } from 'react-icons/gi';
import { HiOutlineInformationCircle } from 'react-icons/hi';

interface PartDropSettingsProps {
  settings: {
    partDropEnabled: boolean;
    partDropChannelId: string;
    partDropChance: number;
  };
  handleChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

export function PartDropSettings({ settings, handleChange }: PartDropSettingsProps) {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <GiBigGear className="text-primary mr-3" size={24} />
        <h2 className="text-xl font-semibold text-white">Part Drop Settings</h2>
      </div>
      <p className="text-slate-300 mb-4">Configure random fishing rod part drops in a specific channel.</p>
      <div className="space-y-6">
        <div className="relative flex items-center mt-2">
          <div className="flex items-center h-5">
            <input
              type="checkbox"
              id="partDropEnabled"
              name="partDropEnabled"
              checked={settings.partDropEnabled}
              onChange={handleChange}
              className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
            />
          </div>
          <div className="ml-3 text-white">
            <label htmlFor="partDropEnabled" className="text-base font-medium flex items-center">
              Enable Part Drops
              <div className="group relative ml-2">
                <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={18} />
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                  When enabled, the bot will randomly drop fishing rod parts in the configured channel.
                </div>
              </div>
            </label>
            <p className="text-slate-400 text-sm mt-1">Randomly drop parts for users to claim.</p>
          </div>
        </div>

        <div className="space-y-4">
          <div>
            <label htmlFor="partDropChannelId" className="block text-sm font-medium text-slate-300 mb-2">
              Drop Channel ID
            </label>
            <input
              type="text"
              id="partDropChannelId"
              name="partDropChannelId"
              value={settings.partDropChannelId || ''}
              onChange={handleChange}
              className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              placeholder="Enter Discord Channel ID"
            />
            <p className="text-slate-400 text-sm mt-1">The channel where parts will be dropped.</p>
          </div>

          <div>
            <label htmlFor="partDropChance" className="block text-sm font-medium text-slate-300 mb-2">
              Drop Chance (0.0 to 1.0)
            </label>
            <input
              type="number"
              id="partDropChance"
              name="partDropChance"
              value={settings.partDropChance || ''}
              onChange={handleChange}
              min="0"
              max="1"
              step="0.01"
              className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
            />
             <p className="text-slate-400 text-sm mt-1">The probability of a drop occurring each minute (e.g., 0.05 for 5%).</p>
          </div>
        </div>
      </div>
    </div>
  );
} 