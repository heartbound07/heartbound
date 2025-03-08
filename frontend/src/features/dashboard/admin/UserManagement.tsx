import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import { Role } from '@/contexts/auth/types';
import { UserProfileDTO } from '@/config/userService';
import httpClient from '@/lib/api/httpClient';
import { ChevronLeft, ChevronRight, Search, Check, X, Filter, RefreshCw } from 'lucide-react';
import { Badge } from '@/components/ui/valorant/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/valorant/tooltip';

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
  
  // Additional security check - redirect if not admin
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }
  
  // Fetch available roles
  useEffect(() => {
    const fetchRoles = async () => {
      try {
        const response = await httpClient.get('/api/admin/roles');
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
      const response = await httpClient.get('/api/users', {
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
  }, [fetchUsers]);
  
  // Handle page change
  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
    }
  };
  
  // Handle search form submission
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0); // Reset to first page when searching
    fetchUsers();
  };
  
  // Start editing roles for a user
  const startEditingRoles = (userId: string, roles: Role[] = []) => {
    setEditing({ userId, roles: [...roles] });
  };
  
  // Cancel editing
  const cancelEditing = () => {
    setEditing(null);
  };
  
  // Toggle a role for the user being edited
  const toggleRole = (role: Role) => {
    if (!editing) return;
    
    const newRoles = [...editing.roles];
    const roleIndex = newRoles.indexOf(role);
    
    if (roleIndex > -1) {
      // Remove role if it exists
      newRoles.splice(roleIndex, 1);
    } else {
      // Add role if it doesn't exist
      newRoles.push(role);
    }
    
    setEditing({ ...editing, roles: newRoles });
  };
  
  // Save updated roles
  const saveRoles = async () => {
    if (!editing) return;
    
    try {
      // Using the batch assign endpoint
      await httpClient.post('/api/admin/roles/batch-assign', {
        userIds: [editing.userId],
        role: editing.roles
      });
      
      // Update the local state to reflect the change
      setUsers(users.map(user => 
        user.id === editing.userId 
          ? { ...user, roles: editing.roles } 
          : user
      ));
      
      setEditing(null);
    } catch (err) {
      console.error('Error updating roles:', err);
      setError('Failed to update user roles');
    }
  };
  
  // Handle refresh
  const handleRefresh = () => {
    setRefreshing(true);
    fetchUsers().finally(() => {
      setTimeout(() => setRefreshing(false), 500);
    });
  };
  
  // Render role badge with appropriate color
  const renderRoleBadge = (role: Role) => {
    let color;
    switch (role) {
      case 'ADMIN':
        color = 'bg-red-500/20 text-red-300 border-red-500/30';
        break;
      case 'MODERATOR':
        color = 'bg-amber-500/20 text-amber-300 border-amber-500/30';
        break;
      case 'MONARCH':
        color = 'bg-purple-500/20 text-purple-300 border-purple-500/30';
        break;
      default:
        color = 'bg-blue-500/20 text-blue-300 border-blue-500/30';
    }
    
    return (
      <Badge className={`${color} mr-1 mb-1`}>
        {role}
      </Badge>
    );
  };
  
  return (
    <div className="container mx-auto p-6">
      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-2xl font-bold text-white">User Management</h1>
          
          <div className="flex space-x-2">
            <button 
              onClick={handleRefresh} 
              className="p-2 bg-slate-800/50 rounded-md hover:bg-slate-700/50 text-slate-300 transition-colors"
              disabled={isLoading || refreshing}
            >
              <RefreshCw className={`h-5 w-5 ${refreshing ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>
        
        {/* Search and filters */}
        <div className="mb-6">
          <form onSubmit={handleSearch} className="flex space-x-2">
            <div className="relative flex-grow">
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search users..."
                className="w-full p-2 pl-10 bg-slate-800/50 rounded-md border border-white/5 text-white placeholder:text-slate-400 focus:border-primary/30 transition-colors focus:outline-none"
              />
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-slate-400" />
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
            >
              Search
            </button>
          </form>
        </div>
        
        {/* Error message */}
        {error && (
          <div className="mb-4 p-3 bg-red-900/20 border border-red-700/20 rounded-md text-red-200">
            {error}
          </div>
        )}
        
        {/* Users table */}
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-white/5 text-white">
            <thead>
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">
                  User
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">
                  ID
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">
                  Roles
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {isLoading ? (
                <tr>
                  <td colSpan={4} className="px-6 py-4 text-center text-slate-300">
                    Loading users...
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-4 text-center text-slate-300">
                    No users found
                  </td>
                </tr>
              ) : (
                users.map((user) => (
                  <tr key={user.id} className="hover:bg-white/5 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <img
                          src={user.avatar || "/default-avatar.png"}
                          alt={user.username}
                          className="h-8 w-8 rounded-full mr-3"
                        />
                        <div>
                          <div className="text-sm font-medium">
                            {user.displayName || user.username}
                          </div>
                          {user.displayName && (
                            <div className="text-xs text-slate-400">@{user.username}</div>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">
                      <span className="font-mono">{user.id}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {editing && editing.userId === user.id ? (
                        <div className="flex flex-wrap max-w-xs">
                          {availableRoles.map((role) => (
                            <TooltipProvider key={role}>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <button
                                    onClick={() => toggleRole(role)}
                                    className={`mr-1 mb-1 px-2 py-1 rounded border ${
                                      editing.roles.includes(role)
                                        ? 'bg-primary/20 text-primary border-primary/30'
                                        : 'bg-slate-800 text-slate-300 border-slate-700'
                                    }`}
                                  >
                                    {role}
                                  </button>
                                </TooltipTrigger>
                                <TooltipContent>
                                  {editing.roles.includes(role) ? 'Remove role' : 'Add role'}
                                </TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                          ))}
                        </div>
                      ) : (
                        <div className="flex flex-wrap max-w-xs">
                          {user.roles?.map((role) => (
                            <span key={role}>
                              {renderRoleBadge(role)}
                            </span>
                          ))}
                          {(!user.roles || user.roles.length === 0) && (
                            <span className="text-slate-400 text-sm">No roles assigned</span>
                          )}
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
