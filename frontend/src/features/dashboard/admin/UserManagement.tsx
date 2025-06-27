import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import { Role } from '@/contexts/auth/types';
import { UserProfileDTO } from '@/config/userService';
import httpClient from '@/lib/api/httpClient';
import { ChevronLeft, ChevronRight, Search, Check, X, RefreshCw, Plus, Minus } from 'lucide-react';
import { Badge } from '@/components/ui/valorant/badge';
import { FaCoins } from 'react-icons/fa';

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
                        <button
                          onClick={() => startEditingRoles(user.id, user.roles)}
                          className="px-2 py-1 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors text-xs"
                        >
                          Edit Roles
                        </button>
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
    </div>
  );
}

export default UserManagement;
