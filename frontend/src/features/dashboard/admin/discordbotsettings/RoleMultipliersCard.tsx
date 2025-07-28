import React, { ChangeEvent } from 'react';
import { HiOutlineBadgeCheck, HiOutlineInformationCircle } from 'react-icons/hi';
import { DiscordBotSettingsDTO } from '@/config/discordBotService';

interface RoleMultipliersCardProps {
  settings: DiscordBotSettingsDTO;
  handleChange: (e: ChangeEvent<HTMLInputElement>) => void;
  parseRoleMultipliers: (roleMultipliers: string) => Array<{ roleId: string; multiplier: string }>;
}

export const RoleMultipliersCard = ({
  settings,
  handleChange,
  parseRoleMultipliers,
}: RoleMultipliersCardProps) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <HiOutlineBadgeCheck className="text-primary mr-3" size={24} />
        <h2 className="text-xl font-semibold text-white">
          Role Multipliers Configuration
        </h2>
      </div>
      
      <p className="text-slate-300 mb-4">Configure Discord role-based multipliers for credits and XP rewards.</p>
      
      <div className="space-y-6">
        <div className="relative flex items-center mt-2">
          <div className="flex items-center h-5">
            <input
              type="checkbox"
              id="roleMultipliersEnabled"
              name="roleMultipliersEnabled"
              checked={settings.roleMultipliersEnabled}
              onChange={handleChange}
              className="form-checkbox h-5 w-5 rounded text-primary border-slate-600 bg-slate-800 focus:ring-primary focus:ring-offset-slate-900"
            />
          </div>
          <div className="ml-3 text-white">
            <label htmlFor="roleMultipliersEnabled" className="text-base font-medium flex items-center">
              Enable Role Multipliers
              <div className="group relative ml-2">
                <HiOutlineInformationCircle className="text-slate-400 hover:text-primary cursor-help transition-colors" size={18} />
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-64 p-3 bg-slate-950 rounded-md shadow-lg text-sm text-slate-200 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                  When enabled, users with configured Discord roles will receive multiplied credits and XP rewards.
                </div>
              </div>
            </label>
            <p className="text-slate-400 text-sm mt-1">Apply multipliers to credits and XP based on Discord roles</p>
          </div>
        </div>
        
        <div className="space-y-4">
          <div>
            <label htmlFor="roleMultipliers" className="block text-sm font-medium text-slate-300 mb-2">
              Role Multipliers Configuration
            </label>
            <input
              type="text"
              id="roleMultipliers"
              name="roleMultipliers"
              value={settings.roleMultipliers || ''}
              onChange={handleChange}
              className="w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary"
              placeholder="123456789:1.5,987654321:2.0"
            />
            <p className="text-slate-400 text-sm mt-1">
              Format: roleId:multiplier,roleId:multiplier (e.g., 123456789:1.5,987654321:2.0)
            </p>
          </div>
          
          {settings.roleMultipliers && settings.roleMultipliers.trim() !== '' && (
            <div className="bg-slate-800/60 rounded-lg p-4 border border-slate-600/50">
              <h4 className="font-medium text-white mb-3 flex items-center">
                <HiOutlineBadgeCheck className="mr-1.5 text-primary" size={16} />
                Current Role Multipliers
              </h4>
              <div className="space-y-2">
                {parseRoleMultipliers(settings.roleMultipliers).map((entry, index) => (
                  <div key={index} className="flex items-center justify-between bg-slate-700/50 rounded px-3 py-2">
                    <span className="text-slate-300 font-mono text-sm">Role ID: {entry.roleId}</span>
                    <span className="text-primary font-medium">{entry.multiplier}x multiplier</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
        
        <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4 text-sm text-blue-200">
          <h4 className="font-medium flex items-center mb-2">
            <HiOutlineInformationCircle className="mr-1.5" size={16} />
            How Role Multipliers Work
          </h4>
          <ul className="list-disc list-inside space-y-1 ml-1 text-blue-100/80">
            <li>Users with configured roles receive multiplied credits and XP for messages</li>
            <li>If a user has multiple qualifying roles, the highest multiplier is applied</li>
            <li>Multipliers apply to both regular activity credits and XP gains</li>
            <li>Level-up credit bonuses are also multiplied by the role multiplier</li>
            <li>Use role IDs from Discord (enable Developer Mode → right-click role → Copy ID)</li>
            <li>Multiplier values can be decimals (e.g., 1.5 = 50% bonus, 2.0 = 100% bonus)</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default React.memo(RoleMultipliersCard); 