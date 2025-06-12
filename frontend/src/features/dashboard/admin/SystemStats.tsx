import { useState, useEffect } from 'react';
import { useAuth } from '@/contexts/auth';
import { Navigate } from 'react-router-dom';
import httpClient from '@/lib/api/httpClient';

interface JWTCacheStats {
  status: string;
  cacheEnabled: boolean;
  tokenValidation: {
    hitRate: number;
    currentSize: number;
    hitRatePercentage: string;
  };
  claims: {
    hitRate: number;
    currentSize: number;
    hitRatePercentage: string;
  };
  userDetails: {
    hitRate: number;
    currentSize: number;
    hitRatePercentage: string;
  };
  overallPerformance: {
    averageHitRate: number;
    averageHitRatePercentage: string;
    performanceStatus: string;
  };
  totalCacheSize: number;
}

interface CacheConfig {
  tokenValidation: {
    maxSize: number;
    expireAfterWriteMinutes: number;
  };
  claims: {
    maxSize: number;
    expireAfterWriteMinutes: number;
  };
  userDetails: {
    maxSize: number;
    expireAfterWriteMinutes: number;
  };
}

interface HealthStatus {
  status: string;
  cacheSystemHealthy: boolean;
  message: string;
  timestamp: number;
}

export function SystemStats() {
  const { hasRole } = useAuth();
  const [stats, setStats] = useState<JWTCacheStats | null>(null);
  const [config, setConfig] = useState<CacheConfig | null>(null);
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  // Security check
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [statsRes, configRes, healthRes] = await Promise.all([
        httpClient.get('/admin/jwt-cache/stats'),
        httpClient.get('/admin/jwt-cache/config'),
        httpClient.get('/admin/jwt-cache/health')
      ]);

      setStats(statsRes.data);
      setConfig(configRes.data.configuration);
      setHealth(healthRes.data);
      setLastRefresh(new Date());
    } catch (err: any) {
      setError(err.message || 'Failed to fetch system stats');
      console.error('Error fetching system stats:', err);
    } finally {
      setLoading(false);
    }
  };

  const performMaintenance = async () => {
    try {
      await httpClient.post('/admin/jwt-cache/maintenance');
      await fetchData(); // Refresh data
    } catch (err: any) {
      setError(err.message || 'Failed to perform maintenance');
    }
  };

  const invalidateAllCaches = async () => {
    if (!confirm('This will clear all JWT caches and cause temporary performance degradation. Continue?')) {
      return;
    }
    
    try {
      await httpClient.post('/admin/jwt-cache/invalidate-all');
      await fetchData(); // Refresh data
    } catch (err: any) {
      setError(err.message || 'Failed to invalidate caches');
    }
  };

  useEffect(() => {
    fetchData();
    
    // Auto-refresh every 30 seconds
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading && !stats) {
    return (
      <div className="container mx-auto p-6">
        <div className="bg-slate-900 rounded p-6">
          <h1 className="text-xl font-bold text-white mb-4">System Stats - JWT Cache Monitoring</h1>
          <div className="text-slate-400">Loading...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6">
      <div className="bg-slate-900 rounded p-6">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-xl font-bold text-white">System Stats - JWT Cache Monitoring</h1>
          <div className="flex gap-2">
            <button
              onClick={fetchData}
              className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
            >
              Refresh
            </button>
            <button
              onClick={performMaintenance}
              className="px-3 py-1 bg-yellow-600 text-white rounded text-sm hover:bg-yellow-700"
            >
              Maintenance
            </button>
            <button
              onClick={invalidateAllCaches}
              className="px-3 py-1 bg-red-600 text-white rounded text-sm hover:bg-red-700"
            >
              Clear All Caches
            </button>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-900/50 border border-red-700 text-red-200 rounded">
            {error}
          </div>
        )}

        <div className="text-sm text-slate-400 mb-4">
          Last Updated: {lastRefresh.toLocaleTimeString()}
          {stats?.cacheEnabled === false && (
            <span className="ml-4 text-yellow-400">⚠ JWT Caching Disabled</span>
          )}
        </div>

        {/* Health Status */}
        {health && (
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-white mb-2">System Health</h2>
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Status</div>
                <div className={`font-medium ${health.status === 'healthy' ? 'text-green-400' : 'text-red-400'}`}>
                  {health.status.toUpperCase()}
                </div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Cache System</div>
                <div className={`font-medium ${health.cacheSystemHealthy ? 'text-green-400' : 'text-red-400'}`}>
                  {health.cacheSystemHealthy ? 'HEALTHY' : 'UNHEALTHY'}
                </div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Message</div>
                <div className="text-white text-sm">{health.message}</div>
              </div>
            </div>
          </div>
        )}

        {/* Overall Performance */}
        {stats && (
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-white mb-2">Overall Performance</h2>
            <div className="grid grid-cols-4 gap-4">
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Average Hit Rate</div>
                <div className="text-white font-medium">{stats.overallPerformance.averageHitRatePercentage}</div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Performance Status</div>
                <div className={`font-medium ${
                  stats.overallPerformance.performanceStatus === 'Excellent' ? 'text-green-400' :
                  stats.overallPerformance.performanceStatus === 'Good' ? 'text-blue-400' :
                  stats.overallPerformance.performanceStatus === 'Fair' ? 'text-yellow-400' : 'text-red-400'
                }`}>
                  {stats.overallPerformance.performanceStatus}
                </div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Total Cache Size</div>
                <div className="text-white font-medium">{stats.totalCacheSize} entries</div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Cache Status</div>
                <div className={`font-medium ${stats.cacheEnabled ? 'text-green-400' : 'text-red-400'}`}>
                  {stats.cacheEnabled ? 'ENABLED' : 'DISABLED'}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Cache Statistics */}
        {stats && (
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-white mb-2">Cache Statistics</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-700">
                    <th className="text-left text-slate-300 py-2">Cache Type</th>
                    <th className="text-right text-slate-300 py-2">Hit Rate</th>
                    <th className="text-right text-slate-300 py-2">Current Size</th>
                    <th className="text-right text-slate-300 py-2">Max Size</th>
                    <th className="text-right text-slate-300 py-2">TTL (min)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b border-slate-800">
                    <td className="text-white py-2">Token Validation</td>
                    <td className="text-right text-white py-2">{stats.tokenValidation.hitRatePercentage}</td>
                    <td className="text-right text-white py-2">{stats.tokenValidation.currentSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.tokenValidation.maxSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.tokenValidation.expireAfterWriteMinutes}</td>
                  </tr>
                  <tr className="border-b border-slate-800">
                    <td className="text-white py-2">Claims</td>
                    <td className="text-right text-white py-2">{stats.claims.hitRatePercentage}</td>
                    <td className="text-right text-white py-2">{stats.claims.currentSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.claims.maxSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.claims.expireAfterWriteMinutes}</td>
                  </tr>
                  <tr>
                    <td className="text-white py-2">User Details</td>
                    <td className="text-right text-white py-2">{stats.userDetails.hitRatePercentage}</td>
                    <td className="text-right text-white py-2">{stats.userDetails.currentSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.userDetails.maxSize}</td>
                    <td className="text-right text-slate-400 py-2">{config?.userDetails.expireAfterWriteMinutes}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Raw Hit Rates (for detailed analysis) */}
        {stats && (
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-white mb-2">Detailed Metrics</h2>
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Token Validation Hit Rate</div>
                <div className="text-white font-mono">{(stats.tokenValidation.hitRate * 100).toFixed(4)}%</div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">Claims Hit Rate</div>
                <div className="text-white font-mono">{(stats.claims.hitRate * 100).toFixed(4)}%</div>
              </div>
              <div className="bg-slate-800 p-3 rounded">
                <div className="text-slate-400 text-sm">User Details Hit Rate</div>
                <div className="text-white font-mono">{(stats.userDetails.hitRate * 100).toFixed(4)}%</div>
              </div>
            </div>
          </div>
        )}

        {/* Performance Tips */}
        <div className="bg-slate-800 p-4 rounded">
          <h3 className="text-white font-medium mb-2">Performance Analysis</h3>
          <div className="text-sm text-slate-300 space-y-1">
            <div>• Hit rates above 85% indicate excellent performance</div>
            <div>• Hit rates below 50% may indicate cache tuning needed</div>
            <div>• Large cache sizes relative to max size may indicate need for size increase</div>
            <div>• Run maintenance to clean up expired entries</div>
            <div>• Only invalidate all caches during maintenance windows</div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default SystemStats; 