import React, { useState } from 'react';
import { UserProfileDTO } from '@/config/userService';
import { Role } from '@/contexts/auth/types';
import { X, User as UserIcon, Shield, CreditCard, Package, Trash2, Minus, Plus, RefreshCw } from 'lucide-react';
import httpClient from '@/lib/api/httpClient';
import { FaCoins } from 'react-icons/fa';
import { Badge } from '@/components/ui/valorant/badge';

// Types for inventory items
interface UserInventoryItem {
  itemId: string;
  name: string;
  description: string;
  category: string;
  thumbnailUrl: string;
  imageUrl: string;
  quantity: number;
  price: number;
}

interface UserManagementModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: UserProfileDTO | null;
  availableRoles: Role[];
  onUserUpdate: (updatedUser: Partial<UserProfileDTO>) => void;
  onUserDelete: (userId: string) => void;
  showToast: (message: string, type: 'success' | 'error' | 'info') => void;
}

const UserManagementModal: React.FC<UserManagementModalProps> = ({
  isOpen,
  onClose,
  user,
  availableRoles,
  onUserUpdate,
  onUserDelete,
  showToast,
}) => {
  const [activeTab, setActiveTab] = useState<'profile' | 'roles' | 'credits' | 'inventory' | 'danger'>('profile');
  const [editingProfile, setEditingProfile] = useState<Partial<UserProfileDTO>>({});
  const [editingRoles, setEditingRoles] = useState<Role[]>([]);
  const [editingCredits, setEditingCredits] = useState<number>(0);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [inventory, setInventory] = useState<UserInventoryItem[]>([]);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [inventoryError, setInventoryError] = useState<string | null>(null);

  const [removalState, setRemovalState] = useState<{
    isRemoving: boolean;
    removingItemId: string | null;
    showConfirmDialog: boolean;
    itemToRemove: UserInventoryItem | null;
  }>({
    isRemoving: false,
    removingItemId: null,
    showConfirmDialog: false,
    itemToRemove: null,
  });

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
      // Reset inventory when user changes
      setInventory([]);
      setInventoryError(null);
    }
  }, [user]);

  React.useEffect(() => {
    if (activeTab === 'inventory' && user && inventory.length === 0) {
      fetchInventory();
    }
  }, [activeTab, user]);

  if (!isOpen || !user) return null;

  const fetchInventory = async () => {
    if (!user) return;
    setInventoryLoading(true);
    setInventoryError(null);
    try {
      const response = await httpClient.get(`/users/${user.id}/inventory`);
      setInventory(response.data);
    } catch (err) {
      console.error('Error fetching user inventory:', err);
      setInventoryError('Failed to load inventory items');
    } finally {
      setInventoryLoading(false);
    }
  };

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

  const openRemovalConfirmation = (item: UserInventoryItem) => {
    setRemovalState({
      isRemoving: false,
      removingItemId: null,
      showConfirmDialog: true,
      itemToRemove: item,
    });
  };

  const closeRemovalConfirmation = () => {
    setRemovalState({
      ...removalState,
      showConfirmDialog: false,
      itemToRemove: null,
    });
  };

  const removeInventoryItem = async () => {
    if (!removalState.itemToRemove || !user) return;

    const itemToRemove = removalState.itemToRemove;
    
    setRemovalState({
      ...removalState,
      isRemoving: true,
      removingItemId: itemToRemove.itemId,
      showConfirmDialog: false,
    });

    try {
      await httpClient.delete(`/users/${user.id}/inventory/${itemToRemove.itemId}`);
      setInventory(prev => prev.filter(item => item.itemId !== itemToRemove.itemId));

      const refundMessage = itemToRemove.price > 0 
        ? ` ${itemToRemove.quantity * itemToRemove.price} credits have been refunded.`
        : '';
      
      showToast(
        `Successfully removed "${itemToRemove.name}" from ${user.username}'s inventory.${refundMessage}`,
        'success'
      );
    } catch (err) {
      console.error('Error removing inventory item:', err);
      let errorMessage = 'Failed to remove item from inventory.';
      if (err instanceof Error) {
        errorMessage += ` ${err.message}`;
      }
      showToast(errorMessage, 'error');
    } finally {
      setRemovalState({
        isRemoving: false,
        removingItemId: null,
        showConfirmDialog: false,
        itemToRemove: null,
      });
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
            <button onClick={() => setActiveTab('inventory')} className={`w-full flex items-center gap-3 p-2 rounded-md text-sm ${activeTab === 'inventory' ? 'bg-primary/20 text-primary' : 'hover:bg-white/5'}`}>
              <Package className="h-4 w-4" /> View Inventory
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
            {activeTab === 'inventory' && (
              <div>
                <h3 className="text-lg font-semibold text-white mb-4">User Inventory</h3>
                {inventoryLoading ? (
                   <div className="flex items-center justify-center py-12">
                     <RefreshCw className="h-8 w-8 animate-spin text-blue-400" />
                     <span className="ml-3 text-slate-300">Loading inventory...</span>
                   </div>
                ) : inventoryError ? (
                  <div className="text-center text-red-400">{inventoryError}</div>
                ) : inventory.length === 0 ? (
                  <div className="text-center text-slate-400 py-12">No items in inventory.</div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {inventory.map((item) => (
                      <div key={item.itemId} className="bg-slate-800/50 border border-white/10 rounded-lg p-4 relative">
                        <div className="relative mb-3">
                          {item.thumbnailUrl ? (
                            <img src={item.thumbnailUrl} alt={item.name} className="w-full h-24 object-cover rounded-md bg-slate-700" />
                          ) : (
                            <div className="w-full h-24 bg-slate-700 rounded-md flex items-center justify-center">
                              <Package className="h-8 w-8 text-slate-500" />
                            </div>
                          )}
                          <div className="absolute top-2 right-2 bg-blue-900/80 text-blue-200 px-2 py-1 rounded text-xs font-medium">
                            x{item.quantity || 0}
                          </div>
                        </div>
                        <div className="space-y-2">
                          <h3 className="font-medium text-white text-sm truncate">{item.name || 'Unknown Item'}</h3>
                          <div className="flex items-center gap-2">
                            <Badge variant="outline" className="text-xs">{item.category || 'UNKNOWN'}</Badge>
                            {item.price > 0 && (
                              <div className="flex items-center text-yellow-400 text-xs">
                                <FaCoins className="h-3 w-3 mr-1" />
                                {item.price}
                              </div>
                            )}
                          </div>
                          <p className="text-slate-400 text-xs line-clamp-2">{item.description}</p>
                          <div className="pt-2">
                            <button
                              onClick={() => openRemovalConfirmation(item)}
                              disabled={removalState.isRemoving && removalState.removingItemId === item.itemId}
                              className="w-full px-3 py-2 bg-red-900/20 text-red-300 hover:bg-red-900/40 disabled:bg-red-900/10 disabled:text-red-500 rounded-md transition-colors text-xs font-medium flex items-center justify-center gap-2"
                            >
                              {removalState.isRemoving && removalState.removingItemId === item.itemId ? (
                                <><RefreshCw className="h-3 w-3 animate-spin" /> Removing...</>
                              ) : (
                                <><X className="h-3 w-3" /> Remove Item</>
                              )}
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
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

      {removalState.showConfirmDialog && removalState.itemToRemove && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-slate-900/95 backdrop-blur-md rounded-xl shadow-2xl border border-white/10 w-full max-w-md mx-4">
            <div className="p-6">
              <h3 className="text-lg font-bold text-white">Confirm Removal</h3>
              <p className="text-slate-300 mt-2 mb-4">
                Are you sure you want to remove "{removalState.itemToRemove.name}" from {user.username}'s inventory?
              </p>
              {removalState.itemToRemove.price > 0 && (
                <div className="flex items-center gap-2 p-3 bg-yellow-900/20 border border-yellow-800/50 rounded-md mb-4">
                  <FaCoins className="h-4 w-4 text-yellow-400" />
                  <span className="text-yellow-300 text-sm">
                    <strong>{(removalState.itemToRemove.quantity || 0) * removalState.itemToRemove.price} credits</strong> will be refunded.
                  </span>
                </div>
              )}
              <div className="mt-6 flex justify-end gap-3">
                <button onClick={closeRemovalConfirmation} className="px-4 py-2 bg-slate-700/50 hover:bg-slate-700/70 text-slate-300 rounded-md transition-colors">
                  Cancel
                </button>
                <button onClick={removeInventoryItem} className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-md transition-colors font-medium">
                  Remove Item
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