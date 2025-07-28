import React, { ChangeEvent } from 'react';
import { FiUsers, FiAward } from 'react-icons/fi';
import { HiOutlineInformationCircle } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface LevelRoleRewardsCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export const LevelRoleRewardsCard = ({ settings, handleChange }: LevelRoleRewardsCardProps) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <FiUsers className="text-primary mr-3" size={24} />
        <h2 className="text-xl font-semibold text-white">
          Level Role Rewards
        </h2>
      </div>
      
      <p className="text-slate-300 mb-4">Configure which Discord role IDs will be granted at each level milestone.</p>
      
      <div className="space-y-4">
        <div className="mt-5 space-y-4">
          <h3 className="text-lg font-semibold text-white flex items-center">
            <FiAward className="mr-2 text-primary" size={20} />
            Discord Role IDs
          </h3>
          <p className="text-sm text-slate-400">
            Configure the Discord role IDs that will be automatically assigned to users when they reach specific level milestones.
          </p>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label htmlFor="level5RoleId" className="block text-sm font-medium text-slate-300">
                Level 5 Role ID
              </label>
              <input
                type="text"
                id="level5RoleId"
                name="level5RoleId"
                value={settings.level5RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level15RoleId" className="block text-sm font-medium text-slate-300">
                Level 15 Role ID
              </label>
              <input
                type="text"
                id="level15RoleId"
                name="level15RoleId"
                value={settings.level15RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level30RoleId" className="block text-sm font-medium text-slate-300">
                Level 30 Role ID
              </label>
              <input
                type="text"
                id="level30RoleId"
                name="level30RoleId"
                value={settings.level30RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level40RoleId" className="block text-sm font-medium text-slate-300">
                Level 40 Role ID
              </label>
              <input
                type="text"
                id="level40RoleId"
                name="level40RoleId"
                value={settings.level40RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level50RoleId" className="block text-sm font-medium text-slate-300">
                Level 50 Role ID
              </label>
              <input
                type="text"
                id="level50RoleId"
                name="level50RoleId"
                value={settings.level50RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level70RoleId" className="block text-sm font-medium text-slate-300">
                Level 70 Role ID
              </label>
              <input
                type="text"
                id="level70RoleId"
                name="level70RoleId"
                value={settings.level70RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
            
            <div>
              <label htmlFor="level100RoleId" className="block text-sm font-medium text-slate-300">
                Level 100 Role ID
              </label>
              <input
                type="text"
                id="level100RoleId"
                name="level100RoleId"
                value={settings.level100RoleId || ''}
                onChange={handleChange}
                className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
                placeholder="Enter Discord Role ID"
              />
            </div>
          </div>
          
          <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3 text-sm text-yellow-200">
            <p className="font-medium flex items-center">
              <HiOutlineInformationCircle className="mr-1.5" size={16} />
              How to find Discord Role IDs
            </p>
            <p className="mt-1 text-yellow-100/80">
              In Discord, enable Developer Mode in Settings → Advanced, then right-click on a role and select "Copy ID".
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default React.memo(LevelRoleRewardsCard); 