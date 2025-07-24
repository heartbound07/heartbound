import React, { useState } from 'react';
import { UserProfileDTO } from '@/config/userService';
import { Role } from '@/contexts/auth/types';
import { X, User as UserIcon, Shield, CreditCard, Trash2, Minus, Plus } from 'lucide-react';
import httpClient from '@/lib/api/httpClient';

interface UserManagementModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: UserProfileDTO | null;
  availableRoles: Role[];
  onUserUpdate: (updatedUser: Partial<UserProfileDTO>) => void;
  onUserDelete: (userId: string) => void;
}

const UserManagementModal: React.FC<UserManagementModalProps> = ({
  isOpen,
  onClose,
  user,
  availableRoles,
  onUserUpdate,
  onUserDelete,
}) => {
  const [activeTab, setActiveTab] = useState<'profile' | 'roles' | 'credits' | 'danger'>('profile');
  const [editingProfile, setEditingProfile] = useState<Partial<UserProfileDTO>>({});
  const [editingRoles, setEditingRoles] = useState<Role[]>([]);
  const [editingCredits, setEditingCredits] = useState<number>(0);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  React.useEffect(() => {
    if (user) {
      setEditingProfile({
        displayName: user.displayName,
        avatar: user.avatar,
        pronouns: user.pronouns,
        about: user.about,
      });
      setEditingRoles(user.roles || []);
      setEditingCredits(user.credits || 0);
      setActiveTab('profile');
    }
  }, [user]);

  if (!isOpen || !user) return null;

  const handleProfileInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setEditingProfile(prev => ({ ...prev, [name]: value }));
  };

  const handleProfileUpdate = async () => {
    try {
      await httpClient.put(`/admin/users/${user.id}/profile`, editingProfile);
      onUserUpdate({ id: user.id, ...editingProfile });
      onClose();
    } catch (err) {
      console.error('Error updating profile:', err);
      // You can add a toast notification here
    }
  };

  const toggleRole = (role: Role) => {
    setEditingRoles(prev => {
      const newRoles = [...prev];
      const index = newRoles.indexOf(role);
      if (index > -1) {
        newRoles.splice(index, 1);
      } else {
        newRoles.push(role);
      }
      return newRoles;
    });
  };

  const saveRoles = async () => {
    try {
      if (!editingRoles.includes('USER')) {
        editingRoles.push('USER');
      }
      await httpClient.post('/admin/roles/batch-assign', {
        userIds: [user.id],
        role: editingRoles,
      });
      onUserUpdate({ id: user.id, roles: editingRoles });
      onClose();
    } catch (err) {
      console.error('Error saving roles:', err);
    }
  };

  const adjustCredits = (amount: number) => {
    setEditingCredits(prev => Math.max(0, prev + amount));
  };

  const saveCredits = async () => {
    try {
      await httpClient.patch(`/users/${user.id}/credits`, { credits: editingCredits });
      onUserUpdate({ id: user.id, credits: editingCredits });
      onClose();
    } catch (err) {
      console.error('Error saving credits:', err);
    }
  };

  const handleDeleteUser = async () => {
    try {
      await httpClient.delete(`/admin/users/${user.id}`);
      onUserDelete(user.id);
      onClose();
    } catch (err) {
      console.error('Error deleting user:', err);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-slate-900/95 backdrop-blur-md rounded-xl shadow-2xl border border-white/10 w-full max-w-4xl max-h-[90vh] mx-4 flex flex-col">
        <div className="flex items-center justify-between p-6 border-b border-white/10 flex-shrink-0">
          <div className="flex items-center gap-3">
            <img src={user.avatar} alt={user.username} className="h-10 w-10 rounded-full" />
            <div>
              <h2 className="text-xl font-bold text-white">Manage {user.displayName || user.username}</h2>
              <p className="text-sm text-slate-400">@{user.username}</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-white/10 rounded-lg transition-colors text-slate-400 hover:text-white">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-grow overflow-hidden flex">
          <div className="w-1/4 border-r border-white/10 p-4 space-y-2">
            <button onClick={() => setActiveTab('profile')} className={`w-full flex items-center gap-3 p-2 rounded-md text-sm ${activeTab === 'profile' ? 'bg-primary/20 text-primary' : 'hover:bg-white/5'}`}>
              <UserIcon className="h-4 w-4" /> Edit Profile
            </button>
            <button onClick={() => setActiveTab('roles')} className={`w-full flex items-center gap-3 p-2 rounded-md text-sm ${activeTab === 'roles' ? 'bg-primary/20 text-primary' : 'hover:bg-white/5'}`}>
              <Shield className="h-4 w-4" /> Manage Roles
            </button>
            <button onClick={() => setActiveTab('credits')} className={`w-full flex items-center gap-3 p-2 rounded-md text-sm ${activeTab === 'credits' ? 'bg-primary/20 text-primary' : 'hover:bg-white/5'}`}>
              <CreditCard className="h-4 w-4" /> Adjust Credits
            </button>
            <button onClick={() => setActiveTab('danger')} className={`w-full flex items-center gap-3 p-2 rounded-md text-sm ${activeTab === 'danger' ? 'bg-red-900/50 text-red-300' : 'hover:bg-white/5'}`}>
              <Trash2 className="h-4 w-4" /> Danger Zone
            </button>
          </div>

          <div className="w-3/4 p-6 overflow-y-auto">
            {activeTab === 'profile' && (
              <div>
                <h3 className="text-lg font-semibold text-white mb-4">Edit Profile</h3>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">User ID</label>
                    <input type="text" value={user.id} readOnly className="w-full pl-4 pr-4 py-2 bg-slate-900/70 rounded-md border border-white/10 text-slate-400 cursor-not-allowed"/>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">Roles</label>
                    <div className="flex flex-wrap gap-2">
                        {user.roles?.map(role => (
                            <div key={role} className="bg-primary/20 text-primary px-2 py-1 rounded-md text-xs font-medium">{role}</div>
                        ))}
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">Display Name</label>
                    <input type="text" name="displayName" value={editingProfile.displayName || ''} onChange={handleProfileInputChange} className="w-full pl-4 pr-4 py-2 bg-slate-800/50 rounded-md border border-white/5 text-white focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-transparent"/>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">Avatar URL</label>
                    <input type="text" name="avatar" value={editingProfile.avatar || ''} onChange={handleProfileInputChange} className="w-full pl-4 pr-4 py-2 bg-slate-800/50 rounded-md border border-white/5 text-white focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-transparent"/>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">Pronouns</label>
                    <input type="text" name="pronouns" value={editingProfile.pronouns || ''} onChange={handleProfileInputChange} className="w-full pl-4 pr-4 py-2 bg-slate-800/50 rounded-md border border-white/5 text-white focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-transparent"/>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">About</label>
                    <textarea name="about" value={editingProfile.about || ''} onChange={handleProfileInputChange} rows={4} className="w-full pl-4 pr-4 py-2 bg-slate-800/50 rounded-md border border-white/5 text-white focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-transparent"></textarea>
                  </div>
                </div>
                <div className="mt-6 flex justify-end">
                  <button onClick={handleProfileUpdate} className="px-4 py-2 bg-primary/80 text-white hover:bg-primary rounded-md transition-colors">Save Profile</button>
                </div>
              </div>
            )}
            {activeTab === 'roles' && (
              <div>
                <h3 className="text-lg font-semibold text-white mb-4">Manage Roles</h3>
                <div className="flex flex-wrap gap-2">
                  {availableRoles.map(role => (
                    <div key={role} onClick={() => toggleRole(role)} className={`cursor-pointer px-3 py-1.5 rounded-md text-sm font-medium ${editingRoles.includes(role) ? 'bg-primary/20 text-primary' : 'bg-slate-800 text-slate-400'}`}>
                      {role}
                    </div>
                  ))}
                </div>
                <div className="mt-6 flex justify-end">
                  <button onClick={saveRoles} className="px-4 py-2 bg-primary/80 text-white hover:bg-primary rounded-md transition-colors">Save Roles</button>
                </div>
              </div>
            )}
            {activeTab === 'credits' && (
              <div>
                <h3 className="text-lg font-semibold text-white mb-4">Adjust Credits</h3>
                <div className="flex items-center space-x-4">
                  <button onClick={() => adjustCredits(-100)} className="p-2 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"><Minus className="h-4 w-4" /></button>
                  <button onClick={() => adjustCredits(-10)} className="p-2 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"><Minus className="h-3 w-3" /></button>
                  <input type="number" value={editingCredits} onChange={(e) => setEditingCredits(parseInt(e.target.value) || 0)} className="w-24 p-2 bg-slate-800/90 border border-white/10 rounded text-center text-white" />
                  <button onClick={() => adjustCredits(10)} className="p-2 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"><Plus className="h-3 w-3" /></button>
                  <button onClick={() => adjustCredits(100)} className="p-2 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"><Plus className="h-4 w-4" /></button>
                </div>
                <div className="mt-6 flex justify-end">
                  <button onClick={saveCredits} className="px-4 py-2 bg-primary/80 text-white hover:bg-primary rounded-md transition-colors">Save Credits</button>
                </div>
              </div>
            )}
            {activeTab === 'danger' && (
              <div>
                <h3 className="text-lg font-semibold text-red-400 mb-4">Danger Zone</h3>
                <div className="p-4 border border-red-800/50 bg-red-900/20 rounded-lg">
                  <h4 className="font-bold text-white">Delete this user</h4>
                  <p className="text-sm text-slate-300 mt-1">
                    Once you delete a user, there is no going back. Please be certain.
                  </p>
                  <button
                    onClick={() => setShowDeleteConfirm(true)}
                    className="mt-4 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-md transition-colors font-medium"
                  >
                    Delete User
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {showDeleteConfirm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-slate-900/95 backdrop-blur-md rounded-xl shadow-2xl border border-white/10 w-full max-w-md mx-4">
            <div className="p-6">
              <h3 className="text-lg font-bold text-white">Are you sure?</h3>
              <p className="text-slate-300 mt-2">
                This will permanently delete the user <span className="font-bold text-white">{user.username}</span>. This action cannot be undone.
              </p>
              <div className="mt-6 flex justify-end gap-3">
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="px-4 py-2 bg-slate-700/50 hover:bg-slate-700/70 text-slate-300 rounded-md transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleDeleteUser}
                  className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-md transition-colors font-medium"
                >
                  Yes, delete user
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagementModal; 