import React from 'react';
import { UserProfileDTO } from '@/config/userService';
import { FaCoins } from 'react-icons/fa';
import { Edit } from 'lucide-react';

interface UsersTableProps {
  users: UserProfileDTO[];
  isLoading: boolean;
  openUserModal: (user: UserProfileDTO) => void;
}

const UsersTable: React.FC<UsersTableProps> = ({ users, isLoading, openUserModal }) => {
  return (
    <div className="flex justify-center overflow-x-auto">
      <table className="text-sm text-left text-slate-300">
        <thead className="text-xs text-slate-500 uppercase bg-slate-900/50">
          <tr>
            <th scope="col" className="px-6 py-3">User</th>
            <th scope="col" className="px-6 py-3">Credits</th>
            <th scope="col" className="px-6 py-3">Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={3} className="px-6 py-4 text-center">
                Loading users...
              </td>
            </tr>
          ) : users.length === 0 ? (
            <tr>
              <td colSpan={3} className="px-6 py-4 text-center">
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
                        src={user.avatar || "/images/default-avatar.png"}
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
                  <div className="flex items-center">
                    <FaCoins className="h-3 w-3 text-yellow-400 mr-2" />
                    <span className="text-yellow-400 font-medium">
                      {user.credits || 0}
                    </span>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-300">
                  <div className="flex space-x-2">
                    <button
                      onClick={() => openUserModal(user)}
                      className="px-2 py-1 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors text-xs flex items-center gap-1"
                    >
                      <Edit className="h-3 w-3" />
                      Manage User
                    </button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

export default UsersTable; 