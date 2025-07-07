import { useState, useEffect, useCallback } from 'react';
import httpClient from '@/lib/api/httpClient';

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

  const fetchAuditEntries = useCallback(async (
    page: number = 0,
    size: number = 20,
    filters: AuditFilters = {}
  ) => {
    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
      });

      // Add filters to params
      Object.entries(filters).forEach(([key, value]) => {
        if (value && value.trim() !== '') {
          params.append(key, value);
        }
      });

      const response = await httpClient.get(`/audit?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      setAuditEntries(data.content);
      setPagination({
        page: data.number,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements,
        first: data.first,
        last: data.last
      });
    } catch (err) {
      console.error('Error fetching audit entries:', err);
      setError('Failed to fetch audit entries. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, []);

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

    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
      });

      const response = await httpClient.get(`/audit/high-severity?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      setAuditEntries(data.content);
      setPagination({
        page: data.number,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements,
        first: data.first,
        last: data.last
      });
    } catch (err) {
      console.error('Error fetching high severity audit entries:', err);
      setError('Failed to fetch high severity audit entries. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchAuditEntriesByUser = useCallback(async (
    userId: string,
    page: number = 0,
    size: number = 20
  ) => {
    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
      });

      const response = await httpClient.get(`/audit/user/${userId}?${params.toString()}`);
      const data: PaginatedAuditResponse = response.data;

      setAuditEntries(data.content);
      setPagination({
        page: data.number,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements,
        first: data.first,
        last: data.last
      });
    } catch (err) {
      console.error('Error fetching audit entries by user:', err);
      setError('Failed to fetch audit entries for user. Please try again.');
      setAuditEntries([]);
    } finally {
      setLoading(false);
    }
  }, []);

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
      'ID', 'Timestamp', 'User ID', 'Action', 'Entity Type', 'Entity ID', 
      'Description', 'IP Address', 'Severity', 'Category', 'Source'
    ];

    if (format === 'csv') {
      const csvContent = [
        headers.join(','),
        ...auditEntries.map(entry => [
          entry.id,
          entry.timestamp,
          entry.userId,
          entry.action,
          entry.entityType || '',
          entry.entityId || '',
          (entry.description || '').replace(/,/g, ';'),
          entry.ipAddress || '',
          entry.severity,
          entry.category,
          entry.source || ''
        ].join(','))
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
      const jsonContent = JSON.stringify(auditEntries, null, 2);
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
  }, [auditEntries]);

  // Load initial data
  useEffect(() => {
    fetchAuditEntries();
    fetchAuditStatistics();
  }, [fetchAuditEntries, fetchAuditStatistics]);

  return {
    auditEntries,
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
    setError
  };
} 