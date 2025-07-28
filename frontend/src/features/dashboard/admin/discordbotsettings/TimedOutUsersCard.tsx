import React from 'react';
import { motion } from 'framer-motion';
import { HiOutlineClock, HiOutlineCheck } from 'react-icons/hi';
import { FiUsers } from 'react-icons/fi';

interface TimedOutUser {
  userId: string;
  username: string;
  avatar: string;
  timeoutLevel: number;
  timeoutExpiry: string;
  livesRemaining: number;
  totalCorrectCounts: number;
  totalMistakes: number;
  bestCount: number;
  timeoutHoursRemaining: number;
  timeoutDuration: string;
}

interface TimedOutUsersCardProps {
  timedOutUsers: TimedOutUser[];
  isLoadingTimeouts: boolean;
  removingTimeouts: Set<string>;
  fetchTimedOutUsers: () => void;
  removeUserTimeout: (userId: string) => void;
}

export const TimedOutUsersCard = ({
  timedOutUsers,
  isLoadingTimeouts,
  removingTimeouts,
  fetchTimedOutUsers,
  removeUserTimeout,
}: TimedOutUsersCardProps) => {
  return (
    <div className="bg-gradient-to-br from-slate-900 to-slate-800 rounded-xl shadow-lg border border-slate-700/50 p-6 mb-8 transition-all duration-300 hover:shadow-xl">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center">
          <HiOutlineClock className="text-primary mr-3" size={24} />
          <h2 className="text-xl font-semibold text-white">
            Timed Out Users
          </h2>
        </div>
        <button
          onClick={fetchTimedOutUsers}
          disabled={isLoadingTimeouts}
          className="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 text-white text-sm rounded-md transition-colors flex items-center"
        >
          {isLoadingTimeouts ? (
            <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-1"></div>
          ) : (
            <HiOutlineCheck className="mr-1" size={16} />
          )}
          Refresh
        </button>
      </div>
      
      <p className="text-slate-300 mb-4">Users currently timed out from the counting game. You can remove their timeout early.</p>
      
      {isLoadingTimeouts ? (
        <div className="flex items-center justify-center py-8">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin mr-3"></div>
          <span className="text-slate-300">Loading timed out users...</span>
        </div>
      ) : timedOutUsers.length === 0 ? (
        <div className="text-center py-8">
          <HiOutlineCheck className="mx-auto text-green-400 mb-2" size={48} />
          <p className="text-slate-300 text-lg font-medium">No users are currently timed out</p>
          <p className="text-slate-400 text-sm">All users have access to the counting game</p>
        </div>
      ) : (
        <div className="space-y-3">
          {timedOutUsers.map((user) => (
            <motion.div
              key={user.userId}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              className="bg-slate-800/60 rounded-lg p-4 border border-slate-700/50 hover:border-slate-600/50 transition-colors"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <div className="w-10 h-10 bg-slate-700 rounded-full flex items-center justify-center">
                    {user.avatar ? (
                      <img
                        src={user.avatar}
                        alt={user.username}
                        className="w-10 h-10 rounded-full"
                        onError={(e) => {
                          const target = e.target as HTMLImageElement;
                          target.style.display = 'none';
                        }}
                      />
                    ) : (
                      <FiUsers className="text-slate-400" size={20} />
                    )}
                  </div>
                  <div>
                    <h3 className="text-white font-medium">{user.username}</h3>
                    <div className="flex items-center space-x-4 text-sm text-slate-400">
                      <span>Timeout Level: {user.timeoutLevel}</span>
                      <span>•</span>
                      <span>Lives: {user.livesRemaining}</span>
                      <span>•</span>
                      <span>Best Count: {user.bestCount}</span>
                    </div>
                  </div>
                </div>
                
                <div className="flex items-center space-x-3">
                  <div className="text-right">
                    <p className="text-white font-medium">{user.timeoutDuration}</p>
                    <p className="text-slate-400 text-sm">remaining</p>
                  </div>
                  <button
                    onClick={() => removeUserTimeout(user.userId)}
                    disabled={removingTimeouts.has(user.userId)}
                    className="px-3 py-2 bg-red-900/40 hover:bg-red-900/60 text-red-200 text-sm rounded-md transition-colors flex items-center disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {removingTimeouts.has(user.userId) ? (
                      <>
                        <div className="w-4 h-4 border-2 border-red-200 border-t-transparent rounded-full animate-spin mr-1"></div>
                        Removing...
                      </>
                    ) : (
                      <>
                        <HiOutlineCheck className="mr-1" size={16} />
                        Remove Timeout
                      </>
                    )}
                  </button>
                </div>
              </div>
              
              <div className="mt-3 pt-3 border-t border-slate-700/50">
                <div className="grid grid-cols-3 gap-4 text-sm">
                  <div className="text-center">
                    <p className="text-slate-400">Correct Counts</p>
                    <p className="text-white font-medium">{user.totalCorrectCounts.toLocaleString()}</p>
                  </div>
                  <div className="text-center">
                    <p className="text-slate-400">Total Mistakes</p>
                    <p className="text-white font-medium">{user.totalMistakes.toLocaleString()}</p>
                  </div>
                  <div className="text-center">
                    <p className="text-slate-400">Success Rate</p>
                    <p className="text-white font-medium">
                      {user.totalCorrectCounts + user.totalMistakes > 0 
                        ? `${Math.round((user.totalCorrectCounts / (user.totalCorrectCounts + user.totalMistakes)) * 100)}%`
                        : '0%'
                      }
                    </p>
                  </div>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
};

export default React.memo(TimedOutUsersCard); 