import { useAuth } from '@/contexts/auth';
import { Navigate, useNavigate } from 'react-router-dom';

export function AdminPanel() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  
  // Additional security check - redirect if not admin
  if (!hasRole('ADMIN')) {
    return <Navigate to="/dashboard" replace />;
  }
  
  const navigateTo = (path: string) => {
    navigate(path);
  };
  
  return (
    <div className="container mx-auto p-6">
      <div className="bg-gradient-to-b from-slate-900/90 to-slate-800/90 backdrop-blur-sm rounded-xl shadow-xl p-6 border border-white/10">
        <h1 className="text-2xl font-bold text-white mb-6">Admin Panel</h1>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {/* Admin Cards */}
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">User Management</h2>
            <p className="text-slate-300 mb-4">Manage users, roles, and permissions</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/admin/users')}
            >
              Manage Users
            </button>
          </div>
          
          {/* Shop Management Card */}
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">Shop Management</h2>
            <p className="text-slate-300 mb-4">Create and manage shop items, categories, and inventory</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/shop/admin')}
            >
              Manage Shop
            </button>
          </div>
          
          {/* Discord Bot Settings Card */}
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">Discord Bot Settings</h2>
            <p className="text-slate-300 mb-4">Configure Discord bot activity and leveling settings</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/admin/discord-settings')}
            >
              Configure Bot
            </button>
          </div>
          
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">Content Management</h2>
            <p className="text-slate-300 mb-4">Manage site content and announcements</p>
            <button className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors">
              Manage Content
            </button>
          </div>
          
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">System Stats</h2>
            <p className="text-slate-300 mb-4">View system statistics and performance metrics</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/admin/system-stats')}
            >
              View Stats
            </button>
          </div>
          
          {/* Audit Management Card */}
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">Audit Management</h2>
            <p className="text-slate-300 mb-4">Monitor and review system audit logs and security events</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/admin/audit')}
            >
              Manage Audits
            </button>
          </div>

          {/* Message Queue Demo Card */}
          <div className="bg-slate-800/50 rounded-lg p-5 border border-white/5 hover:border-primary/20 transition-all duration-300">
            <h2 className="text-lg font-semibold text-white mb-3">Message Queue Demo</h2>
            <p className="text-slate-300 mb-4">Test and monitor WebSocket message queuing system</p>
            <button 
              className="px-4 py-2 bg-primary/20 text-primary hover:bg-primary/30 rounded-md transition-colors"
              onClick={() => navigateTo('/admin/message-queue-demo')}
            >
              Open Demo
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AdminPanel;
