import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import { Role } from '@/contexts/auth/types';
import { UserProfileDTO } from '@/config/userService';
import httpClient from '@/lib/api/httpClient';
import { ChevronLeft, ChevronRight, Search, Check, X, RefreshCw, Plus, Minus, Package } from 'lucide-react';
import { Badge } from '@/components/ui/valorant/badge';
import { FaCoins } from 'react-icons/fa';

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

// User Management page for Admin panel
export function UserManagement() {
  const { hasRole } = useAuth();
  const [users, setUsers] = useState<UserProfileDTO[]>([]);
  const [availableRoles, setAvailableRoles] = useState<Role[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [searchTerm, setSearchTerm] = useState('');
  const [editing, setEditing] = useState<{userId: string, roles: Role[]} | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [editingCredits, setEditingCredits] = useState<{userId: string, credits: number} | null>(null);
  const [inventoryModal, setInventoryModal] = useState<{
    isOpen: boolean;
    userId: string | null;
    username: string | null;
    isLoading: boolean;
    items: UserInventoryItem[];
    error: string | null;
  }>({
    isOpen: false,
    userId: null,
    username: null,
    isLoading: false,
    items: [],
    error: null
  });
  
  // Additional security check - redirect if not admin
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }
  
  // Fetch available roles
  useEffect(() => {
    const fetchRoles = async () => {
      try {
        const response = await httpClient.get('/admin/roles');
        setAvailableRoles(response.data);
      } catch (err) {
        console.error('Error fetching roles:', err);
        setError('Failed to load available roles');
      }
    };
    
    fetchRoles();
  }, []);
  
  // Fetch users with pagination
  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    
    try {
      // Using the existing API with pagination parameters
      const response = await httpClient.get('/users', {
        params: {
          page: currentPage,
          size: pageSize,
          search: searchTerm || undefined
        }
      });
      
      setUsers(response.data.content);
      setTotalPages(response.data.totalPages);
      setIsLoading(false);
    } catch (err) {
      console.error('Error fetching users:', err);
      setError('Failed to load users');
      setIsLoading(false);
    }
  }, [currentPage, pageSize, searchTerm]);
  
  // Load users on initial render and when pagination/search changes
  useEffect(() => {
    fetchUsers();
  }, [fetchUsers, currentPage, pageSize, searchTerm]);
  
  // Handle page change
  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
    }
  };
  
  // Handle search input change
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(e.target.value);
    setCurrentPage(0); // Reset to first page when searching
  };
  
  // Handle search submit
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    fetchUsers();
  };
  
  // Start editing roles for a user
  const startEditingRoles = (userId: string, currentRoles: Role[] = []) => {
    setEditing({ userId, roles: [...currentRoles] });
  };
  
  // Start editing credits for a user
  const startEditingCredits = (userId: string, currentCredits: number = 0) => {
    setEditingCredits({ userId, credits: currentCredits });
  };
  
  // Handle role toggle
  const toggleRole = (role: Role) => {
    if (!editing) return;
    
    setEditing(prev => {
      if (!prev) return prev;
      
      const updatedRoles = [...prev.roles];
      const index = updatedRoles.indexOf(role);
      
      if (index >= 0) {
        // Remove role if it exists
        updatedRoles.splice(index, 1);
      } else {
        // Add role if it doesn't exist
        updatedRoles.push(role);
      }
      
      return { ...prev, roles: updatedRoles };
    });
  };
  
  // Save roles changes
  const saveRoles = async () => {
    if (!editing) return;
    
    try {
      const { userId, roles } = editing;
      
      // Ensure USER role is always included
      if (!roles.includes('USER')) {
        roles.push('USER');
      }
      
      await httpClient.post('/admin/roles/batch-assign', {
        userIds: [userId],
        role: roles
      });
      
      // Update the user in the local state
      setUsers(prev => 
        prev.map(user => 
          user.id === userId ? { ...user, roles } : user
        )
      );
      
      setEditing(null);
    } catch (err) {
      console.error('Error saving roles:', err);
      setError('Failed to update roles');
    }
  };
  
  // Save credits changes
  const saveCredits = async () => {
    if (!editingCredits) return;
    
    try {
      const { userId, credits } = editingCredits;
      
      // Call the API to update credits
      await httpClient.patch(`/users/${userId}/credits`, { credits });
      
      // Update the user in the local state
      setUsers(prev => 
        prev.map(user => 
          user.id === userId ? { ...user, credits } : user
        )
      );
      
      setEditingCredits(null);
    } catch (err) {
      console.error('Error saving credits:', err);
      setError('Failed to update credits');
    }
  };
  
  // Cancel editing
  const cancelEditing = () => {
    setEditing(null);
  };
  
  // Cancel editing credits
  const cancelEditingCredits = () => {
    setEditingCredits(null);
  };
  
  // Handle credits change
  const handleCreditsChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!editingCredits) return;
    
    const newValue = parseInt(e.target.value);
    if (!isNaN(newValue) && newValue >= 0) {
      setEditingCredits(prev => ({
        ...prev!,
        credits: newValue
      }));
    }
  };
  
  // Increment or decrement credits
  const adjustCredits = (amount: number) => {
    if (!editingCredits) return;
    
    const newCredits = Math.max(0, editingCredits.credits + amount);
    setEditingCredits({
      ...editingCredits,
      credits: newCredits
    });
  };
  
  // Refresh user list
  const refreshUsers = async () => {
    setRefreshing(true);
    await fetchUsers();
    setRefreshing(false);
  };

  // Open inventory modal and fetch inventory items
  const openInventoryModal = async (userId: string, username: string) => {
    setInventoryModal({
      isOpen: true,
      userId,
      username,
      isLoading: true,
      items: [],
      error: null
    });

    try {
      const response = await httpClient.get(`/users/${userId}/inventory`);
      setInventoryModal(prev => ({
        ...prev,
        isLoading: false,
        items: response.data
      }));
    } catch (err) {
      console.error('Error fetching user inventory:', err);
      setInventoryModal(prev => ({
        ...prev,
        isLoading: false,
        error: 'Failed to load inventory items'
      }));
    }
  };

  // Close inventory modal
  const closeInventoryModal = () => {
    setInventoryModal({
      isOpen: false,
      userId: null,
      username: null,
      isLoading: false,
      items: [],
      error: null
    });
  };
  
  return (
    <div className="container mx-auto p-6">
      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold text-white">User Management</h1>
          
          <button 
            onClick={refreshUsers}
            className="p-2 bg-slate-800/50 rounded-md hover:bg-slate-700/50 text-slate-300 transition-colors flex items-center gap-2"
            disabled={refreshing}
          >
            <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
        
        {/* Search and filter */}
        <div className="mb-6">
          <form onSubmit={handleSearchSubmit} className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-slate-400" />
              <input 
                type="text"
                value={searchTerm}
                onChange={handleSearchChange}
                placeholder="Search users by username..."
                className="w-full pl-10 pr-4 py-2 bg-slate-800/50 rounded-md border border-white/5 text-white focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-transparent"
              />
            </div>
            <button 
              type="submit"
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
            >
              Search
            </button>
          </form>
        </div>
        
        {error && (
          <div className="mb-4 p-3 bg-red-900/20 border border-red-800/50 text-red-300 rounded-md">
            {error}
          </div>
        )}
        
        {/* Users table */}
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-slate-300">
            <thead className="text-xs text-slate-500 uppercase bg-slate-900/50">
              <tr>
                <th scope="col" className="px-6 py-3">User</th>
                <th scope="col" className="px-6 py-3">User ID</th>
                <th scope="col" className="px-6 py-3">Roles</th>
                <th scope="col" className="px-6 py-3">Credits</th>
                <th scope="col" className="px-6 py-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-4 text-center">
                    Loading users...
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-4 text-center">
                    No users found
                  </td>
                </tr>
              ) : (
                users.map(user => (
                  <tr key={user.id} className="border-b border-slate-800/70 hover:bg-slate-800/30">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <div className="h-10 w-10 flex-shrink-0">
                          <img 
                            className="h-10 w-10 rounded-full object-cover" 
                            src={user.avatar || "/default-avatar.png"} 
                            alt="" 
                          />
                        </div>
                        <div className="ml-4">
                          <div className="font-medium text-white">
                            {user.displayName || user.username}
                          </div>
                          <div className="text-sm text-slate-400">
                            @{user.username}
                          </div>
                        </div>
                      </div>
                    </td>
                    
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm">{user.id}</div>
                    </td>
                    
                    <td className="px-6 py-4">
                      {editing && editing.userId === user.id ? (
                        <div className="flex flex-wrap gap-2 max-w-xs">
                          {availableRoles.map(role => (
                            <div 
                              key={role}
                              onClick={() => toggleRole(role)}
                              className={`
                                cursor-pointer px-2 py-1 rounded-md text-xs font-medium
                                ${editing.roles.includes(role) 
                                  ? 'bg-primary/20 text-primary' 
                                  : 'bg-slate-800 text-slate-400'}
                              `}
                            >
                              {role}
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="flex flex-wrap gap-1 max-w-xs">
                          {user.roles?.map(role => (
                            <Badge key={role} variant="outline" className="text-xs">
                              {role}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </td>
                    
                    <td className="px-6 py-4 whitespace-nowrap">
                      {editingCredits && editingCredits.userId === user.id ? (
                        <div className="flex items-center space-x-2">
                          <button
                            onClick={() => adjustCredits(-10)}
                            className="p-1 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"
                          >
                            <Minus className="h-3 w-3" />
                          </button>
                          
                          <input
                            type="number"
                            value={editingCredits.credits}
                            onChange={handleCreditsChange}
                            min="0"
                            className="w-16 p-1 bg-slate-800/90 border border-white/10 rounded text-center text-white"
                          />
                          
                          <button
                            onClick={() => adjustCredits(10)}
                            className="p-1 bg-slate-700/50 text-slate-300 rounded hover:bg-slate-700/70 transition-colors"
                          >
                            <Plus className="h-3 w-3" />
                          </button>
                          
                          <div className="flex space-x-1 ml-1">
                            <button
                              onClick={saveCredits}
                              className="p-1 bg-green-900/20 text-green-300 rounded hover:bg-green-900/40 transition-colors"
                            >
                              <Check className="h-4 w-4" />
                            </button>
                            <button
                              onClick={cancelEditingCredits}
                              className="p-1 bg-red-900/20 text-red-300 rounded hover:bg-red-900/40 transition-colors"
                            >
                              <X className="h-4 w-4" />
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center">
                          <span className="text-yellow-400 font-medium mr-2">
                            {user.credits || 0}
                          </span>
                          <button
                            onClick={() => startEditingCredits(user.id, user.credits || 0)}
                            className="p-1 bg-yellow-900/20 text-yellow-300 rounded hover:bg-yellow-900/40 transition-colors"
                          >
                            <FaCoins className="h-3 w-3" />
                          </button>
                        </div>
                      )}
                    </td>
                    
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">
                      {editing && editing.userId === user.id ? (
                        <div className="flex space-x-2">
                          <button
                            onClick={saveRoles}
                            className="p-1 bg-green-900/20 text-green-300 rounded hover:bg-green-900/40 transition-colors"
                          >
                            <Check className="h-4 w-4" />
                          </button>
                          <button
                            onClick={cancelEditing}
                            className="p-1 bg-red-900/20 text-red-300 rounded hover:bg-red-900/40 transition-colors"
                          >
                            <X className="h-4 w-4" />
                          </button>
                        </div>
                      ) : (
                        <div className="flex space-x-2">
                          <button
                            onClick={() => startEditingRoles(user.id, user.roles)}
                            className="px-2 py-1 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors text-xs"
                          >
                            Edit Roles
                          </button>
                          <button
                            onClick={() => openInventoryModal(user.id, user.displayName || user.username)}
                            className="px-2 py-1 bg-blue-900/20 text-blue-300 hover:bg-blue-900/40 rounded-md transition-colors text-xs flex items-center gap-1"
                          >
                            <Package className="h-3 w-3" />
                            Inventory
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        
        {/* Pagination */}
        <div className="mt-6 flex items-center justify-between">
          <div className="text-sm text-slate-400">
            Showing page {currentPage + 1} of {totalPages || 1}
          </div>
          <div className="flex space-x-2">
            <select
              value={pageSize}
              onChange={(e) => setPageSize(Number(e.target.value))}
              className="p-2 bg-slate-800/50 rounded-md border border-white/5 text-white"
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
            <button
              onClick={() => handlePageChange(currentPage - 1)}
              disabled={currentPage === 0}
              className="p-2 bg-slate-800/50 rounded-md hover:bg-slate-700/50 text-slate-300 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="h-5 w-5" />
            </button>
            <button
              onClick={() => handlePageChange(currentPage + 1)}
              disabled={currentPage === totalPages - 1 || totalPages === 0}
              className="p-2 bg-slate-800/50 rounded-md hover:bg-slate-700/50 text-slate-300 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronRight className="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>

      {/* Inventory Modal */}
      {inventoryModal.isOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-slate-900/95 backdrop-blur-md rounded-xl shadow-2xl border border-white/10 w-full max-w-4xl max-h-[80vh] mx-4">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-6 border-b border-white/10">
              <div className="flex items-center gap-3">
                <Package className="h-6 w-6 text-blue-400" />
                <h2 className="text-xl font-bold text-white">
                  {inventoryModal.username}'s Inventory
                </h2>
              </div>
              <button
                onClick={closeInventoryModal}
                className="p-2 hover:bg-white/10 rounded-lg transition-colors text-slate-400 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 max-h-[60vh] overflow-y-auto">
              {inventoryModal.isLoading ? (
                <div className="flex items-center justify-center py-12">
                  <RefreshCw className="h-8 w-8 animate-spin text-blue-400" />
                  <span className="ml-3 text-slate-300">Loading inventory...</span>
                </div>
              ) : inventoryModal.error ? (
                <div className="flex items-center justify-center py-12">
                  <div className="text-center">
                    <X className="h-12 w-12 text-red-400 mx-auto mb-3" />
                    <p className="text-red-300 text-lg">{inventoryModal.error}</p>
                  </div>
                </div>
              ) : inventoryModal.items.length === 0 ? (
                <div className="flex items-center justify-center py-12">
                  <div className="text-center">
                    <Package className="h-12 w-12 text-slate-500 mx-auto mb-3" />
                    <p className="text-slate-400 text-lg">No items found</p>
                    <p className="text-slate-500 text-sm mt-1">This user doesn't have any inventory items.</p>
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {inventoryModal.items.map((item) => (
                    <div
                      key={item.itemId}
                      className="bg-slate-800/50 border border-white/10 rounded-lg p-4 hover:bg-slate-800/70 transition-colors"
                    >
                      {/* Item Image */}
                      <div className="relative mb-3">
                        {item.thumbnailUrl ? (
                          <img
                            src={item.thumbnailUrl}
                            alt={item.name}
                            className="w-full h-24 object-cover rounded-md bg-slate-700"
                            onError={(e) => {
                              e.currentTarget.style.display = 'none';
                              e.currentTarget.nextElementSibling?.classList.remove('hidden');
                            }}
                          />
                        ) : null}
                        <div className={`w-full h-24 bg-slate-700 rounded-md flex items-center justify-center ${item.thumbnailUrl ? 'hidden' : ''}`}>
                          <Package className="h-8 w-8 text-slate-500" />
                        </div>
                        
                        {/* Quantity Badge */}
                        <div className="absolute top-2 right-2 bg-blue-900/80 text-blue-200 px-2 py-1 rounded text-xs font-medium">
                          x{item.quantity || 0}
                        </div>
                      </div>

                      {/* Item Details */}
                      <div className="space-y-2">
                        <h3 className="font-medium text-white text-sm truncate" title={item.name || 'Unknown Item'}>
                          {item.name || 'Unknown Item'}
                        </h3>
                        
                        <div className="flex items-center gap-2">
                          <Badge variant="outline" className="text-xs">
                            {item.category || 'UNKNOWN'}
                          </Badge>
                          {item.price !== null && item.price !== undefined && (
                            <div className="flex items-center text-yellow-400 text-xs">
                              <FaCoins className="h-3 w-3 mr-1" />
                              {item.price}
                            </div>
                          )}
                        </div>

                        {item.description && item.description.trim() !== '' && (
                          <p className="text-slate-400 text-xs line-clamp-2" title={item.description}>
                            {item.description}
                          </p>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="border-t border-white/10 p-4 flex justify-between items-center">
              <div className="text-sm text-slate-400">
                {inventoryModal.items.length > 0 && (
                  <>Total items: {inventoryModal.items.length}</>
                )}
              </div>
              <button
                onClick={closeInventoryModal}
                className="px-4 py-2 bg-slate-700/50 hover:bg-slate-700/70 text-slate-300 rounded-md transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default UserManagement;
