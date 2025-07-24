import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import { Role } from '@/contexts/auth/types';
import { UserProfileDTO } from '@/config/userService';
import httpClient from '@/lib/api/httpClient';
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { Toast } from '@/components/Toast';
import UserManagementFilters from './UserManagementFilters';
import UsersTable from './UsersTable';
import UserManagementModal from './UserManagementModal';

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
  const [refreshing, setRefreshing] = useState(false);

  // Modal states
  const [selectedUser, setSelectedUser] = useState<UserProfileDTO | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Add state for toast notifications
  const [toast, setToast] = useState<{
    show: boolean;
    message: string;
    type: 'success' | 'error' | 'info';
  }>({
    show: false,
    message: '',
    type: 'info'
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
    } catch (err) {
      console.error('Error fetching users:', err);
      setError('Failed to load users');
    } finally {
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
  
  const openUserModal = (user: UserProfileDTO) => {
    setSelectedUser(user);
    setIsModalOpen(true);
  };

  const closeUserModal = () => {
    setSelectedUser(null);
    setIsModalOpen(false);
  };
  
  const handleUserUpdate = (updatedUser: Partial<UserProfileDTO>) => {
    setUsers(prev => prev.map(u => u.id === updatedUser.id ? { ...u, ...updatedUser } : u));
    showToast('User updated successfully', 'success');
  };

  const handleUserDelete = (userId: string) => {
    setUsers(prev => prev.filter(u => u.id !== userId));
    showToast('User deleted successfully', 'success');
  };

  // Helper function to show toast notifications
  const showToast = (message: string, type: 'success' | 'error' | 'info') => {
    setToast({
      show: true,
      message,
      type
    });
  };

  // Helper function to close toast notifications
  const closeToast = () => {
    setToast({
      show: false,
      message: '',
      type: 'info'
    });
  };

  // Refresh user list
  const refreshUsers = async () => {
    setRefreshing(true);
    await fetchUsers();
    setRefreshing(false);
  };
  
  return (
    <div className="container mx-auto p-6 text-center">
      <div className="inline-block text-left bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
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
        
        <UserManagementFilters
          searchTerm={searchTerm}
          onSearchChange={handleSearchChange}
          onSearchSubmit={handleSearchSubmit}
        />
        
        {error && (
          <div className="mb-4 p-3 bg-red-900/20 border border-red-800/50 text-red-300 rounded-md">
            {error}
          </div>
        )}
        
        <UsersTable
          users={users}
          isLoading={isLoading}
          openUserModal={openUserModal}
        />
        
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

      <UserManagementModal
        isOpen={isModalOpen}
        onClose={closeUserModal}
        user={selectedUser}
        availableRoles={availableRoles}
        onUserUpdate={handleUserUpdate}
        onUserDelete={handleUserDelete}
      />

      {/* Toast Notifications */}
      {toast.show && (
        <div className="fixed top-4 right-4 z-50">
          <Toast
            message={toast.message}
            type={toast.type}
            onClose={closeToast}
          />
        </div>
      )}
    </div>
  );
}

export default UserManagement;
