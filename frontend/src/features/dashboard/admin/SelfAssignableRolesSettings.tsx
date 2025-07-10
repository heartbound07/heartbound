import React from 'react';
import { FiUsers } from 'react-icons/fi';

// Define a specific interface for the props this component expects
interface SelfAssignableRolesSettingsProps {
  settings: {
    age15RoleId: string;
    age16To17RoleId: string;
    age18PlusRoleId: string;
    genderSheHerRoleId: string;
    genderHeHimRoleId: string;
    genderAskRoleId: string;
    rankIronRoleId: string;
    rankBronzeRoleId: string;
    rankSilverRoleId: string;
    rankGoldRoleId: string;
    rankPlatinumRoleId: string;
    rankDiamondRoleId: string;
    ageRolesThumbnailUrl?: string;
    genderRolesThumbnailUrl?: string;
    rankRolesThumbnailUrl?: string;
  };
  handleChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

export const SelfAssignableRolesSettings: React.FC<SelfAssignableRolesSettingsProps> = ({ settings, handleChange }) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center mb-4">
        <FiUsers className="text-primary mr-3" size={24} />
        <h2 className="text-xl font-semibold text-white">
          Self-Assignable Roles
        </h2>
      </div>
      <p className="text-slate-300 mb-6">Configure the role IDs for the /roles command embeds.</p>

      {/* Age Roles */}
      <div className="mb-6 border-b border-slate-700/50 pb-6">
        <h3 className="text-lg font-semibold text-white mb-3">Age Roles</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label htmlFor="age15RoleId" className="block text-sm font-medium text-slate-300">13-15 Role ID</label>
            <input type="text" id="age15RoleId" name="age15RoleId" value={settings.age15RoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="age16To17RoleId" className="block text-sm font-medium text-slate-300">16-17 Role ID</label>
            <input type="text" id="age16To17RoleId" name="age16To17RoleId" value={settings.age16To17RoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="age18PlusRoleId" className="block text-sm font-medium text-slate-300">18+ Role ID</label>
            <input type="text" id="age18PlusRoleId" name="age18PlusRoleId" value={settings.age18PlusRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
        </div>
        <div>
            <label htmlFor="ageRolesThumbnailUrl" className="block text-sm font-medium text-slate-300">Thumbnail URL</label>
            <input type="text" id="ageRolesThumbnailUrl" name="ageRolesThumbnailUrl" value={settings.ageRolesThumbnailUrl || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="https://example.com/image.png" />
        </div>
      </div>

      {/* Gender Roles */}
      <div className="mb-6 border-b border-slate-700/50 pb-6">
        <h3 className="text-lg font-semibold text-white mb-3">Gender Roles</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label htmlFor="genderSheHerRoleId" className="block text-sm font-medium text-slate-300">She/Her Role ID</label>
            <input type="text" id="genderSheHerRoleId" name="genderSheHerRoleId" value={settings.genderSheHerRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="genderHeHimRoleId" className="block text-sm font-medium text-slate-300">He/Him Role ID</label>
            <input type="text" id="genderHeHimRoleId" name="genderHeHimRoleId" value={settings.genderHeHimRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="genderAskRoleId" className="block text-sm font-medium text-slate-300">Ask Role ID</label>
            <input type="text" id="genderAskRoleId" name="genderAskRoleId" value={settings.genderAskRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
        </div>
        <div>
            <label htmlFor="genderRolesThumbnailUrl" className="block text-sm font-medium text-slate-300">Thumbnail URL</label>
            <input type="text" id="genderRolesThumbnailUrl" name="genderRolesThumbnailUrl" value={settings.genderRolesThumbnailUrl || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="https://example.com/image.png" />
        </div>
      </div>

      {/* Rank Roles */}
      <div>
        <h3 className="text-lg font-semibold text-white mb-3">Rank Roles</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div>
            <label htmlFor="rankIronRoleId" className="block text-sm font-medium text-slate-300">Iron Role ID</label>
            <input type="text" id="rankIronRoleId" name="rankIronRoleId" value={settings.rankIronRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="rankBronzeRoleId" className="block text-sm font-medium text-slate-300">Bronze Role ID</label>
            <input type="text" id="rankBronzeRoleId" name="rankBronzeRoleId" value={settings.rankBronzeRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="rankSilverRoleId" className="block text-sm font-medium text-slate-300">Silver Role ID</label>
            <input type="text" id="rankSilverRoleId" name="rankSilverRoleId" value={settings.rankSilverRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="rankGoldRoleId" className="block text-sm font-medium text-slate-300">Gold Role ID</label>
            <input type="text" id="rankGoldRoleId" name="rankGoldRoleId" value={settings.rankGoldRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="rankPlatinumRoleId" className="block text-sm font-medium text-slate-300">Platinum Role ID</label>
            <input type="text" id="rankPlatinumRoleId" name="rankPlatinumRoleId" value={settings.rankPlatinumRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
          <div>
            <label htmlFor="rankDiamondRoleId" className="block text-sm font-medium text-slate-300">Diamond Role ID</label>
            <input type="text" id="rankDiamondRoleId" name="rankDiamondRoleId" value={settings.rankDiamondRoleId || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="Role ID" />
          </div>
        </div>
        <div>
            <label htmlFor="rankRolesThumbnailUrl" className="block text-sm font-medium text-slate-300">Thumbnail URL</label>
            <input type="text" id="rankRolesThumbnailUrl" name="rankRolesThumbnailUrl" value={settings.rankRolesThumbnailUrl || ''} onChange={handleChange} className="mt-1 block w-full rounded-md bg-slate-800 border border-slate-700 shadow-sm px-3 py-2 text-slate-300 focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary" placeholder="https://example.com/image.png" />
        </div>
      </div>
    </div>
  );
}; 