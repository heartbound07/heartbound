import React, { useState, useCallback } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import { useAudit, AuditFilters } from '@/hooks/useAudit';
import { Toast } from '@/components/Toast';

const SEVERITY_COLORS = {
  LOW: 'text-gray-400',
  INFO: 'text-blue-400',
  WARNING: 'text-yellow-400',
  HIGH: 'text-orange-400',
  CRITICAL: 'text-red-400'
};

const CATEGORY_COLORS = {
  SYSTEM: 'bg-gray-500/20 text-gray-300',
  USER_MANAGEMENT: 'bg-blue-500/20 text-blue-300',
  AUTHENTICATION: 'bg-green-500/20 text-green-300',
  AUTHORIZATION: 'bg-purple-500/20 text-purple-300',
  DATA_ACCESS: 'bg-cyan-500/20 text-cyan-300',
  CONFIGURATION: 'bg-yellow-500/20 text-yellow-300',
  FINANCIAL: 'bg-emerald-500/20 text-emerald-300',
  SECURITY: 'bg-red-500/20 text-red-300'
};

export function AuditPanel() {
  const { hasRole } = useAuth();
  const {
    auditEntries,
    statistics,
    loading,
    error,
    pagination,
    fetchAuditEntries,
    fetchHighSeverityEntries,
    exportAuditData,
    cleanupOldEntries,
    getUserDisplayName,
    setError
  } = useAudit();

  const [filters, setFilters] = useState<AuditFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  const [showCleanupModal, setShowCleanupModal] = useState(false);
  const [cleanupDate, setCleanupDate] = useState('');
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Additional security check - redirect if not admin
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleFilterChange = useCallback((key: keyof AuditFilters, value: string) => {
    setFilters(prev => ({
      ...prev,
      [key]: value
    }));
  }, []);

  const applyFilters = useCallback(() => {
    fetchAuditEntries(0, pagination.size, filters);
  }, [fetchAuditEntries, pagination.size, filters]);

  const clearFilters = useCallback(() => {
    setFilters({});
    fetchAuditEntries(0, pagination.size, {});
  }, [fetchAuditEntries, pagination.size]);

  const handlePageChange = useCallback((newPage: number) => {
    // Validate the new page number to prevent NaN
    const validPage = Math.max(0, Math.floor(Number(newPage) || 0));
    const validSize = Math.max(1, Math.floor(Number(pagination.size) || 20));
    fetchAuditEntries(validPage, validSize, filters);
  }, [fetchAuditEntries, pagination.size, filters]);

  const handleCleanup = useCallback(async () => {
    if (!cleanupDate) {
      setToast({ message: 'Please select a cutoff date', type: 'error' });
      return;
    }

    try {
      const result = await cleanupOldEntries(cleanupDate);
      setToast({ 
        message: `Successfully deleted ${result.deletedCount} old audit entries`, 
        type: 'success' 
      });
      setShowCleanupModal(false);
      setCleanupDate('');
      fetchAuditEntries(0, pagination.size, filters);
    } catch (err) {
      setToast({ message: 'Failed to cleanup audit entries', type: 'error' });
    }
  }, [cleanupDate, cleanupOldEntries, fetchAuditEntries, pagination.size, filters]);

  const formatTimestamp = (timestamp: string) => {
    return new Date(timestamp).toLocaleString();
  };

  const formatUserAgent = (userAgent?: string) => {
    if (!userAgent) return 'N/A';
    if (userAgent.length > 50) {
      return userAgent.substring(0, 50) + '...';
    }
    return userAgent;
  };

  return (
    <div className="container mx-auto p-6">
      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-white mb-2">Audit Log Management</h1>
            <p className="text-slate-300">Monitor and review system audit entries</p>
          </div>

          {/* Statistics Cards */}
          {statistics && (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-4 lg:mt-0">
              <div className="bg-slate-800/50 rounded-lg p-3 border border-white/5">
                <div className="text-sm text-slate-400">Total</div>
                <div className="text-lg font-semibold text-white">{statistics.totalEntries.toLocaleString()}</div>
              </div>
              <div className="bg-slate-800/50 rounded-lg p-3 border border-white/5">
                <div className="text-sm text-slate-400">Last 24h</div>
                <div className="text-lg font-semibold text-white">{statistics.entriesLast24Hours}</div>
              </div>
              <div className="bg-slate-800/50 rounded-lg p-3 border border-white/5">
                <div className="text-sm text-slate-400">Last 7d</div>
                <div className="text-lg font-semibold text-white">{statistics.entriesLast7Days}</div>
              </div>
              <div className="bg-slate-800/50 rounded-lg p-3 border border-white/5">
                <div className="text-sm text-slate-400">Last 30d</div>
                <div className="text-lg font-semibold text-white">{statistics.entriesLast30Days}</div>
              </div>
            </div>
          )}
        </div>

        {/* Controls */}
        <div className="flex flex-wrap gap-4 mb-6">
          <button
            onClick={() => setShowFilters(!showFilters)}
            className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
          >
            {showFilters ? 'Hide Filters' : 'Show Filters'}
          </button>
          
          <button
            onClick={() => fetchHighSeverityEntries()}
            className="px-4 py-2 bg-red-500/20 text-red-300 hover:bg-red-500/30 rounded-md transition-colors"
          >
            High Severity
          </button>

          <button
            onClick={() => exportAuditData('csv')}
            className="px-4 py-2 bg-green-500/20 text-green-300 hover:bg-green-500/30 rounded-md transition-colors"
          >
            Export CSV
          </button>

          <button
            onClick={() => exportAuditData('json')}
            className="px-4 py-2 bg-green-500/20 text-green-300 hover:bg-green-500/30 rounded-md transition-colors"
          >
            Export JSON
          </button>

          <button
            onClick={() => setShowCleanupModal(true)}
            className="px-4 py-2 bg-yellow-500/20 text-yellow-300 hover:bg-yellow-500/30 rounded-md transition-colors"
          >
            Cleanup Old Entries
          </button>
        </div>

        {/* Filters */}
        {showFilters && (
          <div className="bg-slate-800/50 rounded-lg p-4 mb-6 border border-white/5">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">User ID</label>
                <input
                  type="text"
                  value={filters.userId || ''}
                  onChange={(e) => handleFilterChange('userId', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                  placeholder="Filter by user ID"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Action</label>
                <input
                  type="text"
                  value={filters.action || ''}
                  onChange={(e) => handleFilterChange('action', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                  placeholder="Filter by action"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Severity</label>
                <select
                  value={filters.severity || ''}
                  onChange={(e) => handleFilterChange('severity', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                >
                  <option value="">All Severities</option>
                  <option value="LOW">Low</option>
                  <option value="INFO">Info</option>
                  <option value="WARNING">Warning</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Category</label>
                <select
                  value={filters.category || ''}
                  onChange={(e) => handleFilterChange('category', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                >
                  <option value="">All Categories</option>
                  <option value="SYSTEM">System</option>
                  <option value="USER_MANAGEMENT">User Management</option>
                  <option value="AUTHENTICATION">Authentication</option>
                  <option value="AUTHORIZATION">Authorization</option>
                  <option value="DATA_ACCESS">Data Access</option>
                  <option value="CONFIGURATION">Configuration</option>
                  <option value="FINANCIAL">Financial</option>
                  <option value="SECURITY">Security</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">Start Date</label>
                <input
                  type="datetime-local"
                  value={filters.startDate || ''}
                  onChange={(e) => handleFilterChange('startDate', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300 mb-2">End Date</label>
                <input
                  type="datetime-local"
                  value={filters.endDate || ''}
                  onChange={(e) => handleFilterChange('endDate', e.target.value)}
                  className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
                />
              </div>
            </div>

            <div className="flex gap-4 mt-4">
              <button
                onClick={applyFilters}
                className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              >
                Apply Filters
              </button>
              <button
                onClick={clearFilters}
                className="px-4 py-2 bg-slate-600/20 text-slate-300 hover:bg-slate-600/30 rounded-md transition-colors"
              >
                Clear Filters
              </button>
            </div>
          </div>
        )}

        {/* Error Display */}
        {error && (
          <div className="bg-red-500/20 border border-red-500/50 rounded-lg p-4 mb-6">
            <p className="text-red-300">{error}</p>
            <button
              onClick={() => setError(null)}
              className="mt-2 px-3 py-1 bg-red-500/20 text-red-300 hover:bg-red-500/30 rounded text-sm transition-colors"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div className="flex justify-center items-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            <span className="ml-3 text-slate-300">Loading audit entries...</span>
          </div>
        )}

        {/* Audit Table */}
        {!loading && auditEntries.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-600">
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Timestamp</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">User</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Action</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Entity</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Severity</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Category</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">IP Address</th>
                  <th className="text-left py-3 px-4 text-slate-300 font-medium">Description</th>
                </tr>
              </thead>
              <tbody>
                {auditEntries.map((entry) => (
                  <tr key={entry.id} className="border-b border-slate-700/50 hover:bg-slate-800/30">
                    <td className="py-3 px-4 text-slate-300">
                      {formatTimestamp(entry.timestamp)}
                    </td>
                    <td className="py-3 px-4 text-slate-300">
                      <div>
                        <div className="font-medium text-white" title={`Username: ${getUserDisplayName(entry.userId)}`}>
                          {getUserDisplayName(entry.userId)}
                        </div>
                        <div className="text-xs font-mono text-slate-400" title={`User ID: ${entry.userId}`}>
                          ID: {entry.userId}
                        </div>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-white font-medium">
                      {entry.action}
                    </td>
                    <td className="py-3 px-4 text-slate-300">
                      {entry.entityType && (
                        <div>
                          <div className="text-xs text-slate-400">{entry.entityType}</div>
                          {entry.entityId && (
                            <div className="text-xs font-mono text-slate-500">{entry.entityId}</div>
                          )}
                        </div>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <span className={`font-medium ${SEVERITY_COLORS[entry.severity]}`}>
                        {entry.severity}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${CATEGORY_COLORS[entry.category]}`}>
                        {entry.category.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-300 font-mono text-xs">
                      {entry.ipAddress || 'N/A'}
                    </td>
                    <td className="py-3 px-4 text-slate-300 max-w-xs">
                      <div className="truncate" title={entry.description}>
                        {entry.description || 'N/A'}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Empty State */}
        {!loading && auditEntries.length === 0 && (
          <div className="text-center py-8">
            <div className="text-slate-400 mb-4">
              <svg className="mx-auto h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <h3 className="text-lg font-medium text-slate-300 mb-2">No audit entries found</h3>
            <p className="text-slate-400">Try adjusting your filters or check back later.</p>
          </div>
        )}

        {/* Pagination */}
        {!loading && auditEntries.length > 0 && (
          <div className="flex flex-col sm:flex-row justify-between items-center mt-6 gap-4">
            <div className="text-sm text-slate-300">
              Showing {(pagination.page || 0) * (pagination.size || 20) + 1} to {Math.min(((pagination.page || 0) + 1) * (pagination.size || 20), pagination.totalElements || 0)} of {pagination.totalElements || 0} entries
            </div>
            
            <div className="flex gap-2">
              <button
                onClick={() => handlePageChange(Math.max(0, (pagination.page || 0) - 1))}
                disabled={pagination.first}
                className="px-3 py-2 bg-slate-700 text-slate-300 rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-600 transition-colors"
              >
                Previous
              </button>
              
              <span className="px-3 py-2 text-slate-300">
                Page {(pagination.page || 0) + 1} of {pagination.totalPages || 0}
              </span>
              
              <button
                onClick={() => handlePageChange((pagination.page || 0) + 1)}
                disabled={pagination.last}
                className="px-3 py-2 bg-slate-700 text-slate-300 rounded-md disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-600 transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Cleanup Modal */}
      {showCleanupModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-slate-800 rounded-lg p-6 max-w-md w-full mx-4 border border-white/10">
            <h3 className="text-lg font-semibold text-white mb-4">Cleanup Old Audit Entries</h3>
            <p className="text-slate-300 mb-4">
              Delete audit entries older than the specified date. This action cannot be undone.
            </p>
            <div className="mb-4">
              <label className="block text-sm font-medium text-slate-300 mb-2">
                Cutoff Date (entries older than this will be deleted)
              </label>
              <input
                type="datetime-local"
                value={cleanupDate}
                onChange={(e) => setCleanupDate(e.target.value)}
                className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white focus:outline-none focus:ring-2 focus:ring-primary/50"
              />
            </div>
            <div className="flex gap-4">
              <button
                onClick={handleCleanup}
                className="flex-1 px-4 py-2 bg-red-500/20 text-red-300 hover:bg-red-500/30 rounded-md transition-colors"
              >
                Delete Entries
              </button>
              <button
                onClick={() => {
                  setShowCleanupModal(false);
                  setCleanupDate('');
                }}
                className="flex-1 px-4 py-2 bg-slate-600/20 text-slate-300 hover:bg-slate-600/30 rounded-md transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast Notifications */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  );
}

export default AuditPanel; 