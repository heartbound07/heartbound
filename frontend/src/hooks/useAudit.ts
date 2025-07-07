import { useState, useEffect, useCallback } from 'react';
import httpClient from '@/lib/api/httpClient';
import { getUserProfiles, UserProfileDTO } from '@/config/userService';

export interface AuditEntry {
  id: string;
  timestamp: string;
  userId: string;
  action: string;
  entityType?: string;
  entityId?: string;
  description?: string;
  ipAddress?: string;
  userAgent?: string;
  sessionId?: string;
  details?: string;
  severity: 'LOW' | 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL';
  category: 'SYSTEM' | 'USER_MANAGEMENT' | 'AUTHENTICATION' | 'AUTHORIZATION' | 'DATA_ACCESS' | 'CONFIGURATION' | 'FINANCIAL' | 'SECURITY';
  source?: string;
}

export interface AuditFilters {
  userId?: string;
  action?: string;
  entityType?: string;
  severity?: string;
  category?: string;
  startDate?: string;
  endDate?: string;
}

export interface AuditStatistics {
  totalEntries: number;
  entriesLast24Hours: number;
  entriesLast7Days: number;
  entriesLast30Days: number;
}

export interface PaginatedAuditResponse {
  content: AuditEntry[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export function useAudit() {
  const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
  const [userProfiles, setUserProfiles] = useState<Record<string, UserProfileDTO>>({});
  const [statistics, setStatistics] = useState<AuditStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pagination, setPagination] = useState({
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    first: true,
    last: true
  });

  // Helper function to fetch user profiles for audit entries
  const fetchUserProfilesForEntries = useCallback(async (entries: AuditEntry[]) => {
    try {
      // Get unique user IDs from audit entries
      const userIds = [...new Set(entries.map(entry => entry.userId))];
      
      if (userIds.length > 0) {
        const profiles = await getUserProfiles(userIds);
        setUserProfiles(prev => ({ ...prev, ...profiles }));
      }
    } catch (err) {
      console.error('Error fetching user profiles for audit entries:', err);
      // Don't set error state for user profile failures - audit data is still useful
    }
  }, []);

  const fetchAuditEntries = useCallback(async (
    page: number = 0,
    size: number = 20,
    filters: AuditFilters = {}
  ) => {
    setLoading(true);
    setError(null);

    // Validate and sanitize input parameters
    const validPage = Math.max(0, Math.floor(Number(page) || 0));
    const validSize = Math.max(1, Math.min(100, Math.floor(Number(size) || 20)));

    try {
      const params = new URLSearchParams({
        page: validPage.toString(),
        size: validSize.toString(),
      });

      // Add filters to params
      Object.entries(filters).forEach(([key, value]) => {
        if (value && value.trim() !== '') {
          params.append(key, value);
        }
      });

      const response = await httpClient.get(`/audit?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      const entries = data.content || [];
      setAuditEntries(entries);
      setPagination({
        page: Math.max(0, Math.floor(Number(data.number) || 0)),
        size: Math.max(1, Math.floor(Number(data.size) || 20)),
        totalPages: Math.max(0, Math.floor(Number(data.totalPages) || 0)),
        totalElements: Math.max(0, Math.floor(Number(data.totalElements) || 0)),
        first: Boolean(data.first),
        last: Boolean(data.last)
      });

      // Fetch user profiles for the entries
      await fetchUserProfilesForEntries(entries);
    } catch (err) {
      console.error('Error fetching audit entries:', err);
      setError('Failed to fetch audit entries. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, [fetchUserProfilesForEntries]);

  const fetchAuditStatistics = useCallback(async () => {
    try {
      const response = await httpClient.get('/audit/statistics');
      setStatistics(response.data);
    } catch (err) {
      console.error('Error fetching audit statistics:', err);
      setError('Failed to fetch audit statistics.');
    }
  }, []);

  const fetchHighSeverityEntries = useCallback(async (page: number = 0, size: number = 20) => {
    setLoading(true);
    setError(null);

    // Validate and sanitize input parameters
    const validPage = Math.max(0, Math.floor(Number(page) || 0));
    const validSize = Math.max(1, Math.min(100, Math.floor(Number(size) || 20)));

    try {
      const params = new URLSearchParams({
        page: validPage.toString(),
        size: validSize.toString(),
      });

      const response = await httpClient.get(`/audit/high-severity?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      const entries = data.content || [];
      setAuditEntries(entries);
      setPagination({
        page: Math.max(0, Math.floor(Number(data.number) || 0)),
        size: Math.max(1, Math.floor(Number(data.size) || 20)),
        totalPages: Math.max(0, Math.floor(Number(data.totalPages) || 0)),
        totalElements: Math.max(0, Math.floor(Number(data.totalElements) || 0)),
        first: Boolean(data.first),
        last: Boolean(data.last)
      });

      // Fetch user profiles for the entries
      await fetchUserProfilesForEntries(entries);
    } catch (err) {
      console.error('Error fetching high severity audit entries:', err);
      setError('Failed to fetch high severity audit entries. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, [fetchUserProfilesForEntries]);

  const fetchAuditEntriesByUser = useCallback(async (
    userId: string,
    page: number = 0,
    size: number = 20
  ) => {
    setLoading(true);
    setError(null);

    // Validate and sanitize input parameters
    const validPage = Math.max(0, Math.floor(Number(page) || 0));
    const validSize = Math.max(1, Math.min(100, Math.floor(Number(size) || 20)));

    try {
      const params = new URLSearchParams({
        page: validPage.toString(),
        size: validSize.toString(),
      });

      const response = await httpClient.get(`/audit/user/${userId}?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      const entries = data.content || [];
      setAuditEntries(entries);
      setPagination({
        page: Math.max(0, Math.floor(Number(data.number) || 0)),
        size: Math.max(1, Math.floor(Number(data.size) || 20)),
        totalPages: Math.max(0, Math.floor(Number(data.totalPages) || 0)),
        totalElements: Math.max(0, Math.floor(Number(data.totalElements) || 0)),
        first: Boolean(data.first),
        last: Boolean(data.last)
      });

      // Fetch user profiles for the entries
      await fetchUserProfilesForEntries(entries);
    } catch (err) {
      console.error('Error fetching audit entries by user:', err);
      setError('Failed to fetch audit entries for user. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, [fetchUserProfilesForEntries]);

  const cleanupOldEntries = useCallback(async (cutoffDate: string) => {
    setLoading(true);
    setError(null);

    try {
      const response = await httpClient.delete('/audit/cleanup', {
        data: { cutoffDate }
      });

      return response.data;
    } catch (err) {
      console.error('Error cleaning up old audit entries:', err);
      setError('Failed to cleanup old audit entries. Please try again.');
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const exportAuditData = useCallback((format: 'csv' | 'json' = 'csv') => {
    const headers = [
      'ID', 'Timestamp', 'User ID', 'Username', 'Action', 'Entity Type', 'Entity ID', 
      'Description', 'IP Address', 'Severity', 'Category', 'Source'
    ];

    if (format === 'csv') {
      const csvContent = [
        headers.join(','),
        ...auditEntries.map(entry => {
          const profile = userProfiles[entry.userId];
          const username = profile ? (profile.displayName || profile.username || 'Unknown User') : 'Unknown User';
          
          return [
            entry.id,
            entry.timestamp,
            entry.userId,
            username.replace(/,/g, ';'), // Escape commas in username
            entry.action,
            entry.entityType || '',
            entry.entityId || '',
            (entry.description || '').replace(/,/g, ';'),
            entry.ipAddress || '',
            entry.severity,
            entry.category,
            entry.source || ''
          ].join(',');
        })
      ].join('\n');

      const blob = new Blob([csvContent], { type: 'text/csv' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-logs-${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } else {
      // For JSON export, enhance entries with username data
      const enhancedEntries = auditEntries.map(entry => {
        const profile = userProfiles[entry.userId];
        const username = profile ? (profile.displayName || profile.username || 'Unknown User') : 'Unknown User';
        
        return {
          ...entry,
          username
        };
      });
      
      const jsonContent = JSON.stringify(enhancedEntries, null, 2);
      const blob = new Blob([jsonContent], { type: 'application/json' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-logs-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    }
  }, [auditEntries, userProfiles]);

  // Load initial data
  useEffect(() => {
    fetchAuditEntries();
    fetchAuditStatistics();
  }, [fetchAuditEntries, fetchAuditStatistics]);

  // Helper function to get user display name
  const getUserDisplayName = useCallback((userId: string) => {
    const profile = userProfiles[userId];
    if (profile) {
      return profile.displayName || profile.username || userId;
    }
    return userId; // Fallback to user ID if profile not found
  }, [userProfiles]);

  return {
    auditEntries,
    userProfiles,
    statistics,
    loading,
    error,
    pagination,
    fetchAuditEntries,
    fetchAuditStatistics,
    fetchHighSeverityEntries,
    fetchAuditEntriesByUser,
    cleanupOldEntries,
    exportAuditData,
    getUserDisplayName,
    setError
  };
} 